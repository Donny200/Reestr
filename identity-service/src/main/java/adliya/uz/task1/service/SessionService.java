package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.SessionResponse;
import adliya.uz.task1.entity.RefreshToken;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.RefreshTokenRepository;
import adliya.uz.task1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sessions are the non-revoked refresh-token rows of a user grouped by session id. Revoking a session also makes
 * identity-service reject access tokens that carry its id; other services honour the revocation when the access
 * token expires.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    public static final String REASON_USER = "USER_REVOKED";
    public static final String REASON_ADMIN = "ADMIN_REVOKED";
    public static final String REASON_PASSWORD = "PASSWORD_CHANGED";
    public static final String REASON_RECOVERY = "MFA_RECOVERY";
    public static final String REASON_MFA_RESET = "MFA_RESET";
    public static final String REASON_DEACTIVATED = "ACCOUNT_DEACTIVATED";
    public static final String REASON_LOGOUT = "LOGOUT";

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final UserService userService;
    private final SecurityAuditService audit;

    @Transactional(readOnly = true)
    public List<SessionResponse> ownSessions(String currentSessionId) {
        return sessionsOf(userService.getCurrentUser().getId(), currentSessionId);
    }

    @Transactional
    public void revokeOwn(String sessionId, String currentSessionId) {
        User current = userService.getCurrentUser();
        int revoked = refreshTokens.revokeSession(current.getId(), sessionId, Instant.now(), REASON_USER);
        if (revoked == 0) {
            throw new ResourceNotFoundException("Session not found");
        }
        audit.record(current.getId(), current.getId(), SecurityAuditService.SESSION_REVOKED,
                Objects.equals(sessionId, currentSessionId) ? "Current session" : "Another session");
    }

    @Transactional
    public int revokeOwnOthers(String currentSessionId) {
        User current = userService.getCurrentUser();
        int revoked = currentSessionId == null
                ? refreshTokens.revokeAllByUserId(current.getId(), Instant.now(), REASON_USER)
                : refreshTokens.revokeOtherSessions(current.getId(), currentSessionId, Instant.now(), REASON_USER);
        if (revoked > 0) {
            audit.record(current.getId(), current.getId(), SecurityAuditService.SESSIONS_REVOKED, "Other sessions: " + revoked + " token(s)");
        }
        return revoked;
    }

    /** SUPER_ADMIN only: sessions of any user. Organization administrators cannot see sessions of other users. */
    @Transactional(readOnly = true)
    public List<SessionResponse> sessionsForAdmin(Long userId) {
        requireSuperAdmin();
        requireUser(userId);
        return sessionsOf(userId, null);
    }

    @Transactional
    public void revokeForAdmin(Long userId, String sessionId) {
        User admin = requireSuperAdmin();
        requireUser(userId);
        int revoked = refreshTokens.revokeSession(userId, sessionId, Instant.now(), REASON_ADMIN);
        if (revoked == 0) {
            throw new ResourceNotFoundException("Session not found");
        }
        audit.record(userId, admin.getId(), SecurityAuditService.SESSIONS_REVOKED_BY_ADMIN, "One session");
    }

    @Transactional
    public int revokeAllForAdmin(Long userId) {
        User admin = requireSuperAdmin();
        requireUser(userId);
        int revoked = refreshTokens.revokeAllByUserId(userId, Instant.now(), REASON_ADMIN);
        audit.record(userId, admin.getId(), SecurityAuditService.SESSIONS_REVOKED_BY_ADMIN, "All sessions: " + revoked + " token(s)");
        return revoked;
    }

    /** Ends every session of the user for a system reason (password change, recovery, deactivation). */
    @Transactional
    public int revokeAll(Long userId, String reason) {
        return refreshTokens.revokeAllByUserId(userId, Instant.now(), reason);
    }

    @Transactional(readOnly = true)
    public List<adliya.uz.task1.entity.SecurityAuditEvent> securityEvents(Long userId) {
        requireSuperAdmin();
        requireUser(userId);
        return audit.recent(userId);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String sessionId) {
        return sessionId != null && refreshTokens.existsBySessionIdAndRevokedFalseAndExpiresAtAfter(sessionId, Instant.now());
    }

    private List<SessionResponse> sessionsOf(Long userId, String currentSessionId) {
        Map<String, RefreshToken> newest = new LinkedHashMap<>();
        for (RefreshToken token : refreshTokens.findAllByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())) {
            String key = token.getSessionId() == null ? "token-" + token.getId() : token.getSessionId();
            newest.putIfAbsent(key, token);
        }
        return newest.entrySet().stream()
                .map(entry -> new SessionResponse(entry.getKey(), entry.getValue().getCreatedAt(), entry.getValue().getLastUsedAt(),
                        entry.getValue().getExpiresAt(), entry.getValue().getUserAgent(), entry.getKey().equals(currentSessionId)))
                .toList();
    }

    private User requireSuperAdmin() {
        User current = userService.getCurrentUser();
        if (!SystemRole.SUPER_ADMIN.isHeldBy(current)) {
            throw new AccessDeniedException("Only SUPER_ADMIN can manage sessions of other users");
        }
        return current;
    }

    private void requireUser(Long userId) {
        if (!users.existsById(userId)) {
            throw new ResourceNotFoundException("User not found, ID: " + userId);
        }
    }
}
