package adliya.uz.task1.service;

import adliya.uz.task1.config.MfaProperties;
import adliya.uz.task1.config.security.SecretCipher;
import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.config.security.Totp;
import adliya.uz.task1.dto.MfaEnrollmentResponse;
import adliya.uz.task1.dto.MfaStatusResponse;
import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.entity.MfaChallenge;
import adliya.uz.task1.entity.MfaRecoveryCode;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.entity.UserMfa;
import adliya.uz.task1.exception.MfaRequiredException;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.MfaChallengeRepository;
import adliya.uz.task1.repository.MfaRecoveryCodeRepository;
import adliya.uz.task1.repository.UserMfaRepository;
import adliya.uz.task1.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * TOTP second factor for staff accounts. Secrets are encrypted at rest and never returned after enrollment starts,
 * recovery codes are stored only as hashes and shown once, every code is accepted at most once, and nothing here
 * writes a secret or code to the logs or the audit trail.
 */
@Service
@Slf4j
public class MfaService {

    public enum Decision { FULL_SESSION, VERIFY, ENROLL }

    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MfaProperties properties;
    private final SecretCipher cipher;
    private final UserMfaRepository enrollments;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final MfaChallengeRepository challenges;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;
    private final SessionService sessions;
    private final MfaRateLimiter rateLimiter;
    private final Clock clock;

    @Autowired
    public MfaService(MfaProperties properties, SecretCipher cipher, UserMfaRepository enrollments, MfaRecoveryCodeRepository recoveryCodes,
                      MfaChallengeRepository challenges, UserRepository users, PasswordEncoder passwordEncoder, SecurityAuditService audit,
                      SessionService sessions, MfaRateLimiter rateLimiter) {
        this(properties, cipher, enrollments, recoveryCodes, challenges, users, passwordEncoder, audit, sessions, rateLimiter, Clock.systemUTC());
    }

    MfaService(MfaProperties properties, SecretCipher cipher, UserMfaRepository enrollments, MfaRecoveryCodeRepository recoveryCodes,
               MfaChallengeRepository challenges, UserRepository users, PasswordEncoder passwordEncoder, SecurityAuditService audit,
               SessionService sessions, MfaRateLimiter rateLimiter, Clock clock) {
        this.properties = properties;
        this.cipher = cipher;
        this.enrollments = enrollments;
        this.recoveryCodes = recoveryCodes;
        this.challenges = challenges;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.isEnabled() && cipher.available();
    }

    public boolean requiredFor(User user) {
        return enabled() && user.getRole() != null && properties.getRequiredRoles().contains(user.getRole().getName());
    }

    @Transactional(readOnly = true)
    public boolean enrolled(User user) {
        return enabled() && enrollments.findById(user.getId()).map(UserMfa::isConfirmed).orElse(false);
    }

    public List<String> requiredRoles() {
        return properties.getRequiredRoles();
    }

    public Instant enrollmentDeadline() {
        return properties.getEnrollmentDeadline();
    }

    public boolean enrollmentOverdue() {
        Instant deadline = properties.getEnrollmentDeadline();
        return deadline == null || !deadline.isAfter(clock.instant());
    }

    /** What the password step must be followed by for this user. */
    @Transactional(readOnly = true)
    public Decision decide(User user) {
        if (!enabled()) {
            return Decision.FULL_SESSION;
        }
        if (enrolled(user)) {
            return Decision.VERIFY;
        }
        if (requiredFor(user) && enrollmentOverdue()) {
            return Decision.ENROLL;
        }
        return Decision.FULL_SESSION;
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse status(User user) {
        Optional<UserMfa> enrollment = enabled() ? enrollments.findById(user.getId()) : Optional.empty();
        boolean confirmed = enrollment.map(UserMfa::isConfirmed).orElse(false);
        boolean required = requiredFor(user);
        return new MfaStatusResponse(enabled(), required, confirmed, enrollment.map(UserMfa::getConfirmedAt).orElse(null),
                properties.getEnrollmentDeadline(), required && !confirmed && enrollmentOverdue(),
                confirmed ? recoveryCodes.countByUserIdAndUsedAtIsNull(user.getId()) : 0);
    }

    /** Second-factor state attached to the signed-in user's profile; reads the enrollment once. */
    @Transactional(readOnly = true)
    public UserResponse.MfaSummary summary(User user) {
        boolean required = requiredFor(user);
        boolean enrolled = enrolled(user);
        return new UserResponse.MfaSummary(required, enrolled, properties.getEnrollmentDeadline(),
                required && !enrolled && enrollmentOverdue());
    }

    @Transactional(readOnly = true)
    public Set<Long> enrolledUserIds() {
        return enabled() ? new HashSet<>(enrollments.findConfirmedUserIds()) : Set.of();
    }

    // ---- challenge -------------------------------------------------------------------------------------------------

    /** Issues the single-use challenge that bridges the password step and the second factor. */
    @Transactional
    public IssuedChallenge createChallenge(User user, MfaChallenge.Purpose purpose) {
        Instant now = clock.instant();
        String raw = SecureTokens.randomUrlToken(32);
        Instant expiresAt = now.plus(properties.getChallengeTtl());
        challenges.save(MfaChallenge.builder().tokenHash(SecureTokens.sha256Hex(raw)).userId(user.getId()).purpose(purpose)
                .createdAt(now).expiresAt(expiresAt).build());
        return new IssuedChallenge(raw, expiresAt);
    }

    @Transactional(readOnly = true)
    public User userOfChallenge(String rawToken, MfaChallenge.Purpose purpose) {
        return enabledUser(requireChallenge(rawToken, purpose).getUserId());
    }

    /** Completes a VERIFY challenge with a TOTP or recovery code. Using a recovery code ends every other session. */
    @Transactional(noRollbackFor = MfaRequiredException.class)
    public User verify(String rawToken, String code, String clientAddress) {
        rateLimiter.acquire(clientAddress);
        MfaChallenge challenge = requireChallenge(rawToken, MfaChallenge.Purpose.VERIFY);
        rateLimiter.acquire("user:" + challenge.getUserId());
        User user = enabledUser(challenge.getUserId());
        UserMfa enrollment = enrollments.findById(user.getId()).filter(UserMfa::isConfirmed)
                .orElseThrow(() -> new MfaRequiredException("Sign in again"));
        if (!accept(user, enrollment, code, true)) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            challenges.save(challenge);
            audit.record(user.getId(), user.getId(), SecurityAuditService.MFA_VERIFICATION_FAILED, "Attempt " + challenge.getAttempts());
            throw new MfaRequiredException("Invalid verification code");
        }
        challenge.setUsedAt(clock.instant());
        challenges.save(challenge);
        return user;
    }

    @Transactional
    public void consumeChallenge(String rawToken) {
        challenges.findByTokenHash(SecureTokens.sha256HexOfPresented(rawToken)).ifPresent(challenge -> {
            challenge.setUsedAt(clock.instant());
            challenges.save(challenge);
        });
    }

    // ---- enrollment ------------------------------------------------------------------------------------------------

    /** Starts (or restarts a pending) enrollment; the secret is returned once so the user can add it to an app. */
    @Transactional
    public MfaEnrollmentResponse startEnrollment(User user) {
        requireEnabled();
        if (enrollments.findById(user.getId()).map(UserMfa::isConfirmed).orElse(false)) {
            throw new IllegalStateException("Two-factor authentication is already enabled; disable it before enrolling again");
        }
        String secret = Totp.generateSecret();
        Instant now = clock.instant();
        enrollments.save(UserMfa.builder().userId(user.getId()).secretCiphertext(cipher.encrypt(secret))
                .confirmedAt(null).lastUsedStep(null).createdAt(now).updatedAt(now).build());
        return new MfaEnrollmentResponse(secret, Totp.otpauthUri(properties.getIssuer(), user.getEmail(), secret), properties.getIssuer(), user.getEmail());
    }

    /** Confirms the pending enrollment with a first valid code and returns the recovery codes exactly once. */
    @Transactional(noRollbackFor = MfaRequiredException.class)
    public List<String> confirmEnrollment(User user, String code, String clientAddress) {
        requireEnabled();
        rateLimiter.acquire(clientAddress);
        rateLimiter.acquire("user:" + user.getId());
        UserMfa enrollment = enrollments.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Start enrollment before confirming it"));
        if (enrollment.isConfirmed()) {
            throw new IllegalStateException("Two-factor authentication is already enabled");
        }
        OptionalLong step = Totp.matchingStep(cipher.decrypt(enrollment.getSecretCiphertext()), code, clock.instant().getEpochSecond(), properties.getWindow());
        if (step.isEmpty()) {
            throw new MfaRequiredException("Invalid verification code");
        }
        Instant now = clock.instant();
        enrollment.setConfirmedAt(now);
        enrollment.setLastUsedStep(step.getAsLong());
        enrollment.setUpdatedAt(now);
        enrollments.save(enrollment);
        List<String> codes = replaceRecoveryCodes(user);
        audit.record(user.getId(), user.getId(), SecurityAuditService.MFA_ENROLLED, "TOTP enrolled; " + codes.size() + " recovery codes issued");
        return codes;
    }

    @Transactional(noRollbackFor = MfaRequiredException.class)
    public void disable(User user, String password, String code, String clientAddress) {
        UserMfa enrollment = requireReauthenticated(user, password, code, clientAddress);
        recoveryCodes.deleteAllByUserId(user.getId());
        enrollments.delete(enrollment);
        audit.record(user.getId(), user.getId(), SecurityAuditService.MFA_DISABLED, "Disabled by the user");
    }

    @Transactional(noRollbackFor = MfaRequiredException.class)
    public List<String> regenerateRecoveryCodes(User user, String password, String code, String clientAddress) {
        requireReauthenticated(user, password, code, clientAddress);
        List<String> codes = replaceRecoveryCodes(user);
        audit.record(user.getId(), user.getId(), SecurityAuditService.MFA_RECOVERY_CODES_REGENERATED, codes.size() + " new recovery codes");
        return codes;
    }

    /** SUPER_ADMIN removes a user's second factor (e.g. lost phone and codes). The user re-enrolls at the next sign-in. */
    @Transactional
    public void adminReset(User admin, Long targetUserId) {
        if (!SystemRole.SUPER_ADMIN.isHeldBy(admin)) {
            throw new AccessDeniedException("Only SUPER_ADMIN can reset another user's second factor");
        }
        if (admin.getId() != null && admin.getId().equals(targetUserId)) {
            throw new AccessDeniedException("Your own second factor can only be disabled with your password and a valid code");
        }
        User target = users.findById(targetUserId).orElseThrow(() -> new ResourceNotFoundException("User not found, ID: " + targetUserId));
        recoveryCodes.deleteAllByUserId(target.getId());
        enrollments.deleteById(target.getId());
        int revoked = sessions.revokeAll(target.getId(), SessionService.REASON_MFA_RESET);
        audit.record(target.getId(), admin.getId(), SecurityAuditService.MFA_ADMIN_RESET, "Second factor removed; " + revoked + " session token(s) revoked");
        log.info("MFA reset for user {} by user {}", target.getId(), admin.getId());
    }

    @Transactional
    public int purgeExpiredChallenges() {
        return challenges.deleteExpired(clock.instant());
    }

    // ---- internals -------------------------------------------------------------------------------------------------

    private boolean accept(User user, UserMfa enrollment, String code, boolean loginFlow) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.replaceAll("[\\s-]", "").matches("\\d{" + Totp.DIGITS + "}")) {
            OptionalLong step = Totp.matchingStep(cipher.decrypt(enrollment.getSecretCiphertext()), normalized,
                    clock.instant().getEpochSecond(), properties.getWindow());
            if (step.isEmpty() || (enrollment.getLastUsedStep() != null && enrollment.getLastUsedStep() >= step.getAsLong())) {
                return false;
            }
            enrollment.setLastUsedStep(step.getAsLong());
            enrollment.setUpdatedAt(clock.instant());
            enrollments.save(enrollment);
            return true;
        }
        Optional<MfaRecoveryCode> recovery = recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), recoveryHash(normalized));
        if (recovery.isEmpty()) {
            return false;
        }
        recovery.get().setUsedAt(clock.instant());
        recoveryCodes.save(recovery.get());
        long remaining = recoveryCodes.countByUserIdAndUsedAtIsNull(user.getId());
        int revoked = loginFlow ? sessions.revokeAll(user.getId(), SessionService.REASON_RECOVERY) : 0;
        audit.record(user.getId(), user.getId(), SecurityAuditService.MFA_RECOVERY_CODE_USED,
                "Recovery code used; " + remaining + " left" + (loginFlow ? "; " + revoked + " existing session token(s) revoked" : ""));
        return true;
    }

    private List<String> replaceRecoveryCodes(User user) {
        recoveryCodes.deleteAllByUserId(user.getId());
        Map<String, String> codesByHash = new LinkedHashMap<>();
        while (codesByHash.size() < properties.getRecoveryCodeCount()) {
            String code = randomRecoveryCode();
            codesByHash.putIfAbsent(recoveryHash(code), code);
        }
        Instant now = clock.instant();
        codesByHash.keySet().forEach(codeHash ->
                recoveryCodes.save(MfaRecoveryCode.builder().userId(user.getId()).codeHash(codeHash).createdAt(now).build()));
        return List.copyOf(codesByHash.values());
    }

    /**
     * Confirms a sensitive change by a signed-in user with the current password and a fresh code from the
     * confirmed enrollment, which is returned.
     */
    private UserMfa requireReauthenticated(User user, String password, String code, String clientAddress) {
        requireEnabled();
        rateLimiter.acquire(clientAddress);
        requirePassword(user, password);
        UserMfa enrollment = enrollments.findById(user.getId()).filter(UserMfa::isConfirmed)
                .orElseThrow(() -> new IllegalStateException("Two-factor authentication is not enabled"));
        if (!accept(user, enrollment, code, false)) {
            throw new MfaRequiredException("Invalid verification code");
        }
        return enrollment;
    }

    private User enabledUser(Long userId) {
        return users.findById(userId).filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .orElseThrow(() -> new MfaRequiredException("Sign in again"));
    }

    private MfaChallenge requireChallenge(String rawToken, MfaChallenge.Purpose purpose) {
        return challenges.findByTokenHash(SecureTokens.sha256HexOfPresented(rawToken))
                .filter(challenge -> challenge.getPurpose() == purpose && challenge.isUsable(clock.instant(), properties.getMaxAttemptsPerChallenge()))
                .orElseThrow(() -> new MfaRequiredException("Sign in again"));
    }

    private void requirePassword(User user, String password) {
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new IllegalStateException("Two-factor authentication is not configured on this server");
        }
    }

    private static String randomRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < RECOVERY_LENGTH; i++) {
            if (i == RECOVERY_LENGTH / 2) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return code.toString();
    }

    private static String recoveryHash(String code) {
        return SecureTokens.sha256Hex(code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT));
    }

    public record IssuedChallenge(String rawToken, Instant expiresAt) {}
}
