package adliya.uz.task1.service;

import adliya.uz.task1.config.PasswordResetProperties;
import adliya.uz.task1.dto.PasswordResetLinkResponse;
import adliya.uz.task1.entity.PasswordResetChannel;
import adliya.uz.task1.entity.PasswordResetToken;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.InvalidPasswordResetTokenException;
import adliya.uz.task1.exception.PasswordResetUnavailableException;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.PasswordResetTokenRepository;
import adliya.uz.task1.repository.RefreshTokenRepository;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");

    @Mock PasswordResetTokenRepository tokens;
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordResetMailer mailer;
    @Mock UserService userService;

    private PasswordResetProperties properties;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        properties = new PasswordResetProperties();
        properties.setTokenTtl(Duration.ofMinutes(30));
        properties.setPublicBaseUrl("https://reestr.example/");
        service = new PasswordResetService(tokens, users, refreshTokens, passwordEncoder, properties, mailer, userService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void onlySuperAdminCanInitiateAnAdministrativeReset() {
        when(userService.getCurrentUser()).thenReturn(user(2L, "ROLE_ORG_ADMIN", true));

        assertThatThrownBy(() -> service.initiateByAdmin(7L)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(tokens, refreshTokens, passwordEncoder, mailer);
        verify(users, never()).findById(any());
    }

    @Test
    void adminCannotIssueAResetLinkForTheirOwnAccount() {
        User admin = user(1L, "ROLE_SUPER_ADMIN", true);
        when(userService.getCurrentUser()).thenReturn(admin);
        assertThatThrownBy(() -> service.initiateByAdmin(1L)).isInstanceOf(AccessDeniedException.class);
        verify(users, never()).findById(any());
    }

    @Test
    void adminResetStoresOnlyTheHashSupersedesOlderTokensAndReturnsTheLinkOnce() {
        User admin = user(1L, "ROLE_SUPER_ADMIN", true);
        User target = user(7L, "ROLE_MODERATOR", true);
        when(userService.getCurrentUser()).thenReturn(admin);
        when(users.findById(7L)).thenReturn(Optional.of(target));

        PasswordResetLinkResponse response = service.initiateByAdmin(7L);

        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(response.resetPath()).startsWith("/reset-password?token=");
        String rawToken = URLDecoder.decode(response.resetPath().substring("/reset-password?token=".length()), StandardCharsets.UTF_8);
        assertThat(rawToken).hasSizeGreaterThanOrEqualTo(43).doesNotContain("=", "+", "/");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        var order = inOrder(tokens);
        order.verify(tokens).invalidateOutstanding(target, NOW);
        order.verify(tokens).save(saved.capture());
        PasswordResetToken stored = saved.getValue();
        assertThat(stored.getTokenHash()).isEqualTo(SecureTokens.sha256Hex(rawToken)).hasSize(64).isNotEqualTo(rawToken);
        assertThat(stored.getUser()).isSameAs(target);
        assertThat(stored.getChannel()).isEqualTo(PasswordResetChannel.ADMIN);
        assertThat(stored.getInitiatedByUserId()).isEqualTo(1L);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(stored.getUsedAt()).isNull();
        assertThat(target.getPassword()).isEqualTo("hash-7");
        verifyNoInteractions(passwordEncoder, mailer, refreshTokens);
    }

    @Test
    void adminResetRejectsUnknownAndDisabledAccounts() {
        when(userService.getCurrentUser()).thenReturn(user(1L, "ROLE_SUPER_ADMIN", true));
        when(users.findById(8L)).thenReturn(Optional.empty());
        when(users.findById(9L)).thenReturn(Optional.of(user(9L, "ROLE_MODERATOR", false)));

        assertThatThrownBy(() -> service.initiateByAdmin(8L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.initiateByAdmin(9L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void completingAResetHashesTheNewPasswordConsumesTheTokenAndEndsEverySession() {
        User target = user(7L, "ROLE_MODERATOR", true);
        target.setMustChangePassword(true);
        PasswordResetToken token = token(target, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(SecureTokens.sha256Hex("raw-token"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("New-password-2026")).thenReturn("$2a$new-hash");

        User result = service.complete(" raw-token ", "New-password-2026");

        assertThat(result).isSameAs(target);
        assertThat(target.getPassword()).isEqualTo("$2a$new-hash");
        assertThat(target.getMustChangePassword()).isFalse();
        assertThat(target.getCredentialsUpdatedAt()).isEqualTo(NOW);
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        assertThat(token.isUsable(NOW.plusSeconds(1))).isFalse();
        verify(tokens).save(token);
        verify(tokens).invalidateOutstanding(target, NOW);
        verify(users).save(target);
        verify(refreshTokens).revokeAllByUser(target);
        verifyNoInteractions(mailer);
    }

    @Test
    void expiredUsedSupersededUnknownAndDisabledTokensAreAllRejectedTheSameWay() {
        User target = user(7L, "ROLE_MODERATOR", true);
        PasswordResetToken expired = token(target, NOW.minusSeconds(1));
        PasswordResetToken used = token(target, NOW.plusSeconds(60));
        used.setUsedAt(NOW.minusSeconds(30));
        PasswordResetToken superseded = token(target, NOW.plusSeconds(60));
        superseded.setInvalidatedAt(NOW.minusSeconds(30));
        User disabled = user(8L, "ROLE_MODERATOR", false);
        PasswordResetToken disabledOwner = token(disabled, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(SecureTokens.sha256Hex("expired"))).thenReturn(Optional.of(expired));
        when(tokens.findByTokenHash(SecureTokens.sha256Hex("used"))).thenReturn(Optional.of(used));
        when(tokens.findByTokenHash(SecureTokens.sha256Hex("superseded"))).thenReturn(Optional.of(superseded));
        when(tokens.findByTokenHash(SecureTokens.sha256Hex("disabled"))).thenReturn(Optional.of(disabledOwner));
        when(tokens.findByTokenHash(SecureTokens.sha256Hex("unknown"))).thenReturn(Optional.empty());

        for (String raw : new String[]{"expired", "used", "superseded", "disabled", "unknown"}) {
            assertThatThrownBy(() -> service.complete(raw, "New-password-2026"))
                    .as(raw).isInstanceOf(InvalidPasswordResetTokenException.class);
        }
        assertThat(target.getPassword()).isEqualTo("hash-7");
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokens, never()).revokeAllByUser(any());
        verify(users, never()).save(any());
    }

    @Test
    void selfServiceRequestBehavesIdenticallyForUnknownAndKnownAddresses() {
        when(mailer.available()).thenReturn(true);
        when(users.findByEmail("nobody@example.test")).thenReturn(Optional.empty());
        User known = user(7L, "ROLE_MODERATOR", true);
        when(users.findByEmail("staff@example.test")).thenReturn(Optional.of(known));
        when(users.findByEmail("off@example.test")).thenReturn(Optional.of(user(8L, "ROLE_MODERATOR", false)));

        service.requestByEmail("nobody@example.test");
        service.requestByEmail("off@example.test");
        verify(tokens, never()).save(any());
        verify(mailer, never()).sendResetLink(anyString(), anyString(), any());

        service.requestByEmail("Staff@Example.test");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq(known.getEmail()), link.capture(), eq(NOW.plus(Duration.ofMinutes(30))));
        assertThat(link.getValue()).startsWith("https://reestr.example/reset-password?token=");
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        String rawToken = URLDecoder.decode(link.getValue().substring(link.getValue().indexOf("token=") + 6), StandardCharsets.UTF_8);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SecureTokens.sha256Hex(rawToken));
        assertThat(saved.getValue().getChannel()).isEqualTo(PasswordResetChannel.SELF_SERVICE);
        assertThat(saved.getValue().getInitiatedByUserId()).isNull();
        verify(tokens).invalidateOutstanding(known, NOW);
    }

    @Test
    void failedDeliveryInvalidatesTheTokenAndStillAnswersLikeAnUnknownAddress() {
        when(mailer.available()).thenReturn(true);
        User known = user(7L, "ROLE_MODERATOR", true);
        when(users.findByEmail("staff@example.test")).thenReturn(Optional.of(known));
        doThrow(new MailSendException("SMTP unreachable")).when(mailer).sendResetLink(anyString(), anyString(), any());

        assertThatCode(() -> service.requestByEmail("staff@example.test")).doesNotThrowAnyException();

        var order = inOrder(tokens);
        order.verify(tokens).invalidateOutstanding(known, NOW);
        order.verify(tokens).save(any());
        order.verify(tokens).invalidateOutstanding(known, NOW);
    }

    @Test
    void selfServiceIsRefusedWithoutVerifiedMailDeliveryAndNeverFallsBackToAnotherChannel() {
        when(mailer.available()).thenReturn(false);
        when(mailer.missingConfiguration()).thenReturn("spring.mail.host is not configured");

        assertThatThrownBy(() -> service.requestByEmail("staff@example.test"))
                .isInstanceOf(PasswordResetUnavailableException.class)
                .hasMessageContaining("spring.mail.host");

        assertThat(service.selfServiceAvailable()).isFalse();
        verifyNoInteractions(tokens, users);
    }

    private static User user(long id, String role, boolean enabled) {
        return User.builder().id(id).email("user" + id + "@example.test").password("hash-" + id)
                .firstName("First").lastName("Last").enabled(enabled)
                .role(Role.builder().name(role).build()).build();
    }

    private static PasswordResetToken token(User user, Instant expiresAt) {
        return PasswordResetToken.builder().id(1L).tokenHash("irrelevant").user(user).channel(PasswordResetChannel.ADMIN)
                .createdAt(NOW.minusSeconds(120)).expiresAt(expiresAt).build();
    }
}
