package adliya.uz.task1.service;

import adliya.uz.task1.config.PasswordResetProperties;
import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.PasswordResetLinkResponse;
import adliya.uz.task1.entity.PasswordResetChannel;
import adliya.uz.task1.entity.PasswordResetToken;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.InvalidPasswordResetTokenException;
import adliya.uz.task1.exception.PasswordResetUnavailableException;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.PasswordResetTokenRepository;
import adliya.uz.task1.repository.RefreshTokenRepository;
import adliya.uz.task1.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Password resets without ever revealing a password: a cryptographically random, single-use, short-lived token is
 * issued, only its SHA-256 hash is stored, a newer reset supersedes older tokens, and completing a reset re-hashes
 * the new password with the shared encoder and ends every existing session.
 */
@Service
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final PasswordResetMailer mailer;
    private final UserService userService;
    private final Clock clock;

    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, RefreshTokenRepository refreshTokens,
                                PasswordEncoder passwordEncoder, PasswordResetProperties properties, PasswordResetMailer mailer,
                                UserService userService) {
        this(tokens, users, refreshTokens, passwordEncoder, properties, mailer, userService, Clock.systemUTC());
    }

    PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, RefreshTokenRepository refreshTokens,
                         PasswordEncoder passwordEncoder, PasswordResetProperties properties, PasswordResetMailer mailer,
                         UserService userService, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.mailer = mailer;
        this.userService = userService;
        this.clock = clock;
    }

    public boolean selfServiceAvailable() {
        return mailer.available();
    }

    /** SUPER_ADMIN starts a reset for any enabled user and receives the single-use link exactly once. */
    @Transactional
    public PasswordResetLinkResponse initiateByAdmin(Long userId) {
        User admin = userService.getCurrentUser();
        if (!SystemRole.SUPER_ADMIN.isHeldBy(admin)) {
            throw new AccessDeniedException("Only SUPER_ADMIN can initiate a password reset");
        }
        if (admin.getId() != null && admin.getId().equals(userId)) {
            throw new AccessDeniedException("Change your own password with the current password; an admin reset link cannot target your own account");
        }
        User target = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found, ID: " + userId));
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new IllegalStateException("The account is disabled; enable it before resetting the password");
        }
        Issued issued = issue(target, PasswordResetChannel.ADMIN, admin.getId());
        log.info("Password reset initiated by user {} for user {}; expires at {}", admin.getId(), target.getId(), issued.expiresAt());
        return new PasswordResetLinkResponse(target.getId(), resetPath(issued.rawToken()), issued.expiresAt());
    }

    /**
     * Self-service request. The outcome is identical whether or not the address belongs to an account, so the
     * response cannot be used to enumerate users. Without verified e-mail delivery the flow is unavailable.
     */
    @Transactional
    public void requestByEmail(String email) {
        if (!mailer.available()) {
            throw new PasswordResetUnavailableException("Self-service password reset is not configured; ask an administrator ("
                    + mailer.missingConfiguration() + ")");
        }
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Optional<User> found = users.findByEmail(normalized);
        if (found.isEmpty()) {
            found = users.findByEmail(email == null ? "" : email.trim());
        }
        if (found.isEmpty() || !Boolean.TRUE.equals(found.get().getEnabled())) {
            log.info("Password reset requested for an address without an active account");
            return;
        }
        User user = found.get();
        Issued issued = issue(user, PasswordResetChannel.SELF_SERVICE, null);
        String link = properties.getPublicBaseUrl().replaceAll("/+$", "") + resetPath(issued.rawToken());
        try {
            mailer.sendResetLink(user.getEmail(), link, issued.expiresAt());
        } catch (RuntimeException failure) {
            // The link never reached the user, so the token must not stay valid; and a failing response here
            // would tell the caller that the address belongs to an account, so the outcome stays identical.
            tokens.invalidateOutstanding(user, clock.instant());
            log.error("Password reset e-mail could not be delivered for user {}; token invalidated", user.getId(), failure);
            return;
        }
        log.info("Password reset link e-mailed to user {}; expires at {}", user.getId(), issued.expiresAt());
    }

    /** Consumes the token, stores the new hash and revokes every session of the user. */
    @Transactional
    public User complete(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHash(SecureTokens.sha256HexOfPresented(rawToken))
                .filter(found -> found.isUsable(now))
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Reset token is invalid, expired or already used"));
        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new InvalidPasswordResetTokenException("Reset token belongs to a disabled account");
        }
        token.setUsedAt(now);
        tokens.save(token);
        tokens.invalidateOutstanding(user, now);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setCredentialsUpdatedAt(now);
        users.save(user);
        refreshTokens.revokeAllByUser(user);
        log.info("Password reset completed for user {} (initiated via {} at {}); all sessions revoked",
                user.getId(), token.getChannel(), token.getCreatedAt());
        return user;
    }

    private Issued issue(User user, PasswordResetChannel channel, Long initiatedBy) {
        Instant now = clock.instant();
        tokens.invalidateOutstanding(user, now);
        String rawToken = SecureTokens.randomUrlToken(32);
        Instant expiresAt = now.plus(properties.getTokenTtl());
        tokens.save(PasswordResetToken.builder()
                .tokenHash(SecureTokens.sha256Hex(rawToken))
                .user(user)
                .channel(channel)
                .initiatedByUserId(initiatedBy)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build());
        return new Issued(rawToken, expiresAt);
    }

    private String resetPath(String rawToken) {
        return properties.getResetPath() + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private record Issued(String rawToken, Instant expiresAt) {}
}
