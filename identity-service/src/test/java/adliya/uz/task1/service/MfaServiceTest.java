package adliya.uz.task1.service;

import adliya.uz.task1.config.MfaProperties;
import adliya.uz.task1.config.security.SecretCipher;
import adliya.uz.task1.config.security.Totp;
import adliya.uz.task1.dto.MfaEnrollmentResponse;
import adliya.uz.task1.entity.MfaChallenge;
import adliya.uz.task1.entity.MfaRecoveryCode;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.SecurityAuditEvent;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.entity.UserMfa;
import adliya.uz.task1.exception.MfaRequiredException;
import adliya.uz.task1.exception.RateLimitExceededException;
import adliya.uz.task1.repository.MfaChallengeRepository;
import adliya.uz.task1.repository.MfaRecoveryCodeRepository;
import adliya.uz.task1.repository.SecurityAuditEventRepository;
import adliya.uz.task1.repository.UserMfaRepository;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** In-memory repositories back the service so the whole enrollment / challenge / recovery life cycle is exercised. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T09:00:00Z");

    @Mock UserMfaRepository enrollments;
    @Mock MfaRecoveryCodeRepository recoveryCodes;
    @Mock MfaChallengeRepository challenges;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SecurityAuditEventRepository auditEvents;
    @Mock SessionService sessions;

    private final Map<Long, UserMfa> storedEnrollments = new HashMap<>();
    private final List<MfaRecoveryCode> storedCodes = new ArrayList<>();
    private final Map<String, MfaChallenge> storedChallenges = new HashMap<>();
    private final List<SecurityAuditEvent> audit = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private MfaProperties properties;
    private MutableClock clock;
    private MfaService service;
    private User admin;
    private User orgAdmin;
    private User moderator;

    @BeforeEach
    void setUp() {
        properties = new MfaProperties();
        properties.setEnabled(true);
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setPerClientLimit(10);
        clock = new MutableClock(NOW);
        admin = user(1L, "ROLE_SUPER_ADMIN");
        orgAdmin = user(2L, "ROLE_ORG_ADMIN");
        moderator = user(3L, "ROLE_MODERATOR");
        when(users.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(Map.of(1L, admin, 2L, orgAdmin, 3L, moderator).get(call.<Long>getArgument(0))));
        when(enrollments.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(storedEnrollments.get(call.<Long>getArgument(0))));
        when(enrollments.save(any())).thenAnswer(call -> { UserMfa e = call.getArgument(0); storedEnrollments.put(e.getUserId(), e); return e; });
        when(enrollments.findConfirmedUserIds()).thenAnswer(call -> storedEnrollments.values().stream().filter(UserMfa::isConfirmed).map(UserMfa::getUserId).toList());
        org.mockito.Mockito.doAnswer(call -> storedEnrollments.remove(call.<Long>getArgument(0))).when(enrollments).deleteById(anyLong());
        org.mockito.Mockito.doAnswer(call -> storedEnrollments.remove(call.<UserMfa>getArgument(0).getUserId())).when(enrollments).delete(any());
        when(recoveryCodes.save(any())).thenAnswer(call -> { MfaRecoveryCode c = call.getArgument(0); if (c.getId() == null) { c.setId(ids.incrementAndGet()); storedCodes.add(c); } return c; });
        when(recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(anyLong(), anyString())).thenAnswer(call -> storedCodes.stream()
                .filter(c -> c.getUserId().equals(call.getArgument(0)) && c.getCodeHash().equals(call.getArgument(1)) && c.getUsedAt() == null).findFirst());
        when(recoveryCodes.countByUserIdAndUsedAtIsNull(anyLong())).thenAnswer(call -> storedCodes.stream()
                .filter(c -> c.getUserId().equals(call.getArgument(0)) && c.getUsedAt() == null).count());
        org.mockito.Mockito.doAnswer(call -> { storedCodes.removeIf(c -> c.getUserId().equals(call.getArgument(0))); return null; }).when(recoveryCodes).deleteAllByUserId(anyLong());
        when(challenges.save(any())).thenAnswer(call -> { MfaChallenge c = call.getArgument(0); storedChallenges.put(c.getTokenHash(), c); return c; });
        when(challenges.findByTokenHash(anyString())).thenAnswer(call -> Optional.ofNullable(storedChallenges.get(call.<String>getArgument(0))));
        when(auditEvents.save(any())).thenAnswer(call -> { audit.add(call.getArgument(0)); return call.getArgument(0); });
        when(passwordEncoder.matches(eq("secret-password"), anyString())).thenReturn(true);
        when(sessions.revokeAll(anyLong(), anyString())).thenReturn(2);
        service = new MfaService(properties, new SecretCipher(properties), enrollments, recoveryCodes, challenges, users, passwordEncoder,
                new SecurityAuditService(auditEvents), sessions, new MfaRateLimiter(properties, clock), clock);
    }

    @Test
    void administrativeRolesMustEnrollUnlessAGracePeriodIsConfigured() {
        assertThat(service.decide(admin)).isEqualTo(MfaService.Decision.ENROLL);
        assertThat(service.decide(orgAdmin)).isEqualTo(MfaService.Decision.ENROLL);
        assertThat(service.decide(moderator)).isEqualTo(MfaService.Decision.FULL_SESSION);

        properties.setEnrollmentDeadline(NOW.plus(Duration.ofDays(14)));
        assertThat(service.decide(admin)).isEqualTo(MfaService.Decision.FULL_SESSION);
        assertThat(service.status(admin).required()).isTrue();
        assertThat(service.status(admin).enrollmentOverdue()).isFalse();
        clock.advance(Duration.ofDays(15));
        assertThat(service.decide(admin)).isEqualTo(MfaService.Decision.ENROLL);
        assertThat(service.status(admin).enrollmentOverdue()).isTrue();

        properties.setEnabled(false);
        properties.setEncryptionKey("");
        service = new MfaService(properties, new SecretCipher(properties), enrollments, recoveryCodes, challenges, users, passwordEncoder,
                new SecurityAuditService(auditEvents), sessions, new MfaRateLimiter(properties, clock), clock);
        assertThat(service.decide(admin)).isEqualTo(MfaService.Decision.FULL_SESSION);
    }

    @Test
    void enrollmentStoresOnlyTheEncryptedSecretAndHashedRecoveryCodesAndAuditsWithoutThem() {
        MfaEnrollmentResponse enrollment = service.startEnrollment(admin);
        assertThat(enrollment.otpauthUri()).contains("secret=" + enrollment.secret()).contains("issuer=ReestrGov");
        UserMfa stored = storedEnrollments.get(1L);
        assertThat(stored.getSecretCiphertext()).startsWith("v1:").doesNotContain(enrollment.secret());
        assertThat(stored.isConfirmed()).isFalse();
        assertThat(service.decide(admin)).as("pending enrollment is not an enrollment").isEqualTo(MfaService.Decision.ENROLL);

        assertThatThrownBy(() -> service.confirmEnrollment(admin, "000000", "203.0.113.1")).isInstanceOf(MfaRequiredException.class);
        List<String> codes = service.confirmEnrollment(admin, Totp.code(enrollment.secret(), Totp.step(NOW.getEpochSecond())), "203.0.113.1");
        assertThat(codes).hasSize(10).allMatch(code -> code.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertThat(storedEnrollments.get(1L).isConfirmed()).isTrue();
        assertThat(storedCodes).hasSize(10).allMatch(code -> code.getCodeHash().length() == 64)
                .noneMatch(code -> codes.contains(code.getCodeHash()));
        assertThat(service.decide(admin)).isEqualTo(MfaService.Decision.VERIFY);
        assertThat(service.status(admin).recoveryCodesRemaining()).isEqualTo(10);
        assertThat(audit).extracting(SecurityAuditEvent::getAction).contains("MFA_ENROLLED");
        for (SecurityAuditEvent event : audit) {
            assertThat(event.getDetails()).doesNotContain(enrollment.secret());
            for (String code : codes) {
                assertThat(event.getDetails()).doesNotContain(code);
            }
        }
        assertThatThrownBy(() -> service.startEnrollment(admin)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void challengeIsSingleUseAndEveryCodeIsAcceptedOnlyOnce() {
        String secret = enroll(admin);
        MfaService.IssuedChallenge challenge = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        assertThat(storedChallenges.values()).extracting(MfaChallenge::getTokenHash).doesNotContain(challenge.rawToken());
        String code = Totp.code(secret, Totp.step(clock.instant().getEpochSecond()));

        assertThatThrownBy(() -> service.verify(challenge.rawToken(), "999999", "203.0.113.1")).isInstanceOf(MfaRequiredException.class);
        assertThat(service.verify(challenge.rawToken(), code, "203.0.113.1").getId()).isEqualTo(1L);
        assertThatThrownBy(() -> service.verify(challenge.rawToken(), code, "203.0.113.1"))
                .as("challenge consumed").isInstanceOf(MfaRequiredException.class);

        MfaService.IssuedChallenge second = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        assertThatThrownBy(() -> service.verify(second.rawToken(), code, "203.0.113.2"))
                .as("same time-step code replayed").isInstanceOf(MfaRequiredException.class);
        clock.advance(Duration.ofSeconds(60));
        String later = Totp.code(secret, Totp.step(clock.instant().getEpochSecond()));
        assertThat(service.verify(second.rawToken(), later, "203.0.113.2").getId()).isEqualTo(1L);

        clock.advance(Duration.ofMinutes(10));
        MfaService.IssuedChallenge expired = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        clock.advance(Duration.ofMinutes(6));
        assertThatThrownBy(() -> service.verify(expired.rawToken(), Totp.code(secret, Totp.step(clock.instant().getEpochSecond())), "203.0.113.3"))
                .isInstanceOf(MfaRequiredException.class);
        assertThatThrownBy(() -> service.verify("unknown", "123456", "203.0.113.3")).isInstanceOf(MfaRequiredException.class);
        assertThatThrownBy(() -> service.userOfChallenge(expired.rawToken(), MfaChallenge.Purpose.ENROLL)).isInstanceOf(MfaRequiredException.class);
    }

    @Test
    void recoveryCodeWorksOnceAndEndsOtherSessions() {
        enroll(admin);
        List<String> codes = service.regenerateRecoveryCodes(admin, "secret-password", currentCode(admin), "203.0.113.9");
        String recovery = codes.get(0);
        MfaService.IssuedChallenge first = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        assertThat(service.verify(first.rawToken(), recovery.toLowerCase(), "203.0.113.1").getId()).isEqualTo(1L);
        verify(sessions).revokeAll(1L, SessionService.REASON_RECOVERY);
        assertThat(service.status(admin).recoveryCodesRemaining()).isEqualTo(9);

        MfaService.IssuedChallenge second = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        assertThatThrownBy(() -> service.verify(second.rawToken(), recovery, "203.0.113.2")).isInstanceOf(MfaRequiredException.class);
        assertThat(audit).extracting(SecurityAuditEvent::getAction).contains("MFA_RECOVERY_CODE_USED", "MFA_RECOVERY_CODES_REGENERATED");
        assertThat(audit).extracting(SecurityAuditEvent::getDetails).noneMatch(details -> details != null && details.contains(recovery));
    }

    @Test
    void verificationAttemptsAreRateLimitedPerClientAndPerAccount() {
        properties.setPerClientLimit(3);
        service = new MfaService(properties, new SecretCipher(properties), enrollments, recoveryCodes, challenges, users, passwordEncoder,
                new SecurityAuditService(auditEvents), sessions, new MfaRateLimiter(properties, clock), clock);
        enroll(admin);
        clock.advance(Duration.ofMinutes(16));
        MfaService.IssuedChallenge challenge = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.verify(challenge.rawToken(), "000000", "203.0.113.5")).isInstanceOf(MfaRequiredException.class);
        }
        assertThatThrownBy(() -> service.verify(challenge.rawToken(), "000000", "203.0.113.5"))
                .as("fourth attempt from the same address").isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(() -> service.verify(challenge.rawToken(), currentCode(admin), "203.0.113.6"))
                .as("the account key is exhausted too, whichever address is used").isInstanceOf(RateLimitExceededException.class);
        clock.advance(Duration.ofMinutes(16));
        MfaService.IssuedChallenge fresh = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        assertThat(service.verify(fresh.rawToken(), currentCode(admin), "203.0.113.6").getId()).isEqualTo(1L);
    }

    @Test
    void tooManyWrongCodesDiscardTheChallenge() {
        properties.setMaxAttemptsPerChallenge(2);
        enroll(admin);
        MfaService.IssuedChallenge challenge = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        assertThatThrownBy(() -> service.verify(challenge.rawToken(), "000000", "203.0.113.5")).isInstanceOf(MfaRequiredException.class);
        assertThatThrownBy(() -> service.verify(challenge.rawToken(), "000000", "203.0.113.5")).isInstanceOf(MfaRequiredException.class);
        assertThatThrownBy(() -> service.verify(challenge.rawToken(), currentCode(admin), "203.0.113.5"))
                .as("a correct code no longer helps once the challenge is spent").isInstanceOf(MfaRequiredException.class);
        MfaService.IssuedChallenge fresh = service.createChallenge(admin, MfaChallenge.Purpose.VERIFY);
        assertThat(service.verify(fresh.rawToken(), currentCode(admin), "203.0.113.5").getId()).isEqualTo(1L);
    }

    @Test
    void disableRequiresPasswordAndCodeAndAdminResetIsForSuperAdminOnly() {
        enroll(orgAdmin);
        assertThatThrownBy(() -> service.disable(orgAdmin, "wrong", currentCode(orgAdmin), "203.0.113.7")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.disable(orgAdmin, "secret-password", "000000", "203.0.113.7")).isInstanceOf(MfaRequiredException.class);
        service.disable(orgAdmin, "secret-password", currentCode(orgAdmin), "203.0.113.7");
        assertThat(service.enrolled(orgAdmin)).isFalse();
        assertThat(storedCodes).noneMatch(code -> code.getUserId().equals(2L));
        assertThat(audit).extracting(SecurityAuditEvent::getAction).contains("MFA_DISABLED");

        enroll(orgAdmin);
        assertThatThrownBy(() -> service.adminReset(orgAdmin, 1L)).isInstanceOf(AccessDeniedException.class);
        // A hijacked SUPER_ADMIN session must not be able to drop its own second factor without password and code.
        enroll(admin);
        assertThatThrownBy(() -> service.adminReset(admin, admin.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(service.enrolled(admin)).isTrue();
        service.adminReset(admin, 2L);
        assertThat(service.enrolled(orgAdmin)).isFalse();
        verify(sessions).revokeAll(2L, SessionService.REASON_MFA_RESET);
        assertThat(audit).filteredOn(event -> "MFA_ADMIN_RESET".equals(event.getAction()))
                .singleElement().satisfies(event -> {
                    assertThat(event.getUserId()).isEqualTo(2L);
                    assertThat(event.getActorUserId()).isEqualTo(1L);
                });
    }

    private String enroll(User user) {
        String secret = service.startEnrollment(user).secret();
        service.confirmEnrollment(user, Totp.code(secret, Totp.step(clock.instant().getEpochSecond())), "203.0.113.100");
        clock.advance(Duration.ofSeconds(31));
        return secret;
    }

    private String currentCode(User user) {
        SecretCipher cipher = new SecretCipher(properties);
        clock.advance(Duration.ofSeconds(31));
        return Totp.code(cipher.decrypt(storedEnrollments.get(user.getId()).getSecretCiphertext()), Totp.step(clock.instant().getEpochSecond()));
    }

    private static User user(long id, String role) {
        return User.builder().id(id).firstName("U" + id).lastName("L").email("user" + id + "@example.test").password("hash")
                .role(Role.builder().name(role).build()).enabled(true).build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
