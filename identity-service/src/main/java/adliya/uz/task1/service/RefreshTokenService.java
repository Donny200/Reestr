package adliya.uz.task1.service;

import adliya.uz.task1.config.security.JwtProperties;
import adliya.uz.task1.entity.RefreshToken;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.InvalidRefreshTokenException;
import adliya.uz.task1.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String createRefreshToken(User user) {
        return createSession(user, null).rawToken();
    }

    /** Starts a new session: a fresh session id that every rotated token of this browser session keeps. */
    @Transactional
    public IssuedSession createSession(User user, String userAgent) {
        return issue(user, UUID.randomUUID().toString(), userAgent);
    }

    private IssuedSession issue(User user, String sessionId, String userAgent) {
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new InvalidRefreshTokenException("Password change is required before issuing a refresh token");
        }

        String rawToken = SecureTokens.randomUrlToken(64);
        Instant now = Instant.now();

        RefreshToken entity = RefreshToken.builder()
                .tokenHash(SecureTokens.sha256Hex(rawToken))
                .user(user)
                .sessionId(sessionId)
                .userAgent(userAgent)
                .lastUsedAt(now)
                .expiresAt(now.plusMillis(jwtProperties.getRefreshExpiration()))
                .build();

        refreshTokenRepository.save(entity);
        return new IssuedSession(rawToken, sessionId);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (Boolean.TRUE.equals(stored.getUser().getMustChangePassword())) {
            refreshTokenRepository.revokeAllByUser(stored.getUser());
            throw new InvalidRefreshTokenException("Password change is required before refreshing the session");
        }

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllByUser(stored.getUser());
            throw new InvalidRefreshTokenException("Refresh token reuse detected");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        stored.setRevoked(true);
        stored.setRevokedAt(Instant.now());
        stored.setRevokedReason("ROTATED");
        refreshTokenRepository.save(stored);

        String sessionId = stored.getSessionId() == null ? UUID.randomUUID().toString() : stored.getSessionId();
        IssuedSession next = issue(stored.getUser(), sessionId, stored.getUserAgent());
        return new RotationResult(stored.getUser(), next.rawToken(), sessionId);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(Instant.now());
                    token.setRevokedReason(SessionService.REASON_LOGOUT);
                    refreshTokenRepository.save(token);
                });
    }

    public record RotationResult(User user, String rawToken, String sessionId) {}

    public record IssuedSession(String rawToken, String sessionId) {}
}
