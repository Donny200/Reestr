package adliya.uz.task1.service;

import adliya.uz.task1.dto.SessionResponse;
import adliya.uz.task1.entity.RefreshToken;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.RefreshTokenRepository;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock RefreshTokenRepository refreshTokens;
    @Mock UserRepository users;
    @Mock UserService userService;
    @Mock SecurityAuditService audit;
    @InjectMocks SessionService service;

    @Test
    void ownSessionsAreGroupedBySessionIdWithoutTokenMaterialAndMarkTheCurrentOne() {
        User me = user(5L, "ROLE_ORG_ADMIN");
        when(userService.getCurrentUser()).thenReturn(me);
        Instant now = Instant.now();
        when(refreshTokens.findAllByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq(5L), any())).thenReturn(List.of(
                token(me, "s-current", "Firefox", now),
                token(me, "s-phone", "Safari", now.minusSeconds(60)),
                token(me, "s-phone", "Safari", now.minusSeconds(600))));

        List<SessionResponse> sessions = service.ownSessions("s-current");

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).sessionId()).isEqualTo("s-current");
        assertThat(sessions.get(0).current()).isTrue();
        assertThat(sessions.get(1).current()).isFalse();
        assertThat(sessions.get(1).userAgent()).isEqualTo("Safari");
        assertThat(sessions.toString()).doesNotContain("hash-");
    }

    @Test
    void revokingOwnSessionsIsScopedToTheCallerAndAudited() {
        User me = user(5L, "ROLE_ORG_ADMIN");
        when(userService.getCurrentUser()).thenReturn(me);
        when(refreshTokens.revokeSession(eq(5L), eq("s-phone"), any(), eq(SessionService.REASON_USER))).thenReturn(1);
        when(refreshTokens.revokeSession(eq(5L), eq("someone-else"), any(), eq(SessionService.REASON_USER))).thenReturn(0);

        service.revokeOwn("s-phone", "s-current");
        verify(audit).record(5L, 5L, SecurityAuditService.SESSION_REVOKED, "Another session");
        assertThatThrownBy(() -> service.revokeOwn("someone-else", "s-current")).isInstanceOf(ResourceNotFoundException.class);

        when(refreshTokens.revokeOtherSessions(eq(5L), eq("s-current"), any(), eq(SessionService.REASON_USER))).thenReturn(3);
        assertThat(service.revokeOwnOthers("s-current")).isEqualTo(3);
        verify(refreshTokens, never()).revokeAllByUserId(any(), any(), anyString());
    }

    @Test
    void onlySuperAdminSeesOrRevokesSessionsOfOtherUsers() {
        when(userService.getCurrentUser()).thenReturn(user(5L, "ROLE_ORG_ADMIN"));
        assertThatThrownBy(() -> service.sessionsForAdmin(9L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.revokeForAdmin(9L, "s-1")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.revokeAllForAdmin(9L)).isInstanceOf(AccessDeniedException.class);
        verify(refreshTokens, never()).revokeAllByUserId(any(), any(), anyString());

        when(userService.getCurrentUser()).thenReturn(user(1L, "ROLE_SUPER_ADMIN"));
        when(users.existsById(9L)).thenReturn(true);
        when(refreshTokens.revokeAllByUserId(eq(9L), any(), eq(SessionService.REASON_ADMIN))).thenReturn(2);
        assertThat(service.revokeAllForAdmin(9L)).isEqualTo(2);
        verify(audit).record(9L, 1L, SecurityAuditService.SESSIONS_REVOKED_BY_ADMIN, "All sessions: 2 token(s)");
        when(users.existsById(77L)).thenReturn(false);
        assertThatThrownBy(() -> service.sessionsForAdmin(77L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revokedSessionsAreNoLongerActive() {
        when(refreshTokens.existsBySessionIdAndRevokedFalseAndExpiresAtAfter(eq("alive"), any())).thenReturn(true);
        when(refreshTokens.existsBySessionIdAndRevokedFalseAndExpiresAtAfter(eq("gone"), any())).thenReturn(false);
        assertThat(service.isActive("alive")).isTrue();
        assertThat(service.isActive("gone")).isFalse();
        assertThat(service.isActive(null)).isFalse();
    }

    private static User user(long id, String role) {
        return User.builder().id(id).email("u" + id + "@example.test").role(Role.builder().name(role).build()).enabled(true).build();
    }

    private static RefreshToken token(User user, String sessionId, String agent, Instant created) {
        return RefreshToken.builder().user(user).tokenHash("hash-" + sessionId + created.toEpochMilli()).sessionId(sessionId)
                .userAgent(agent).createdAt(created).lastUsedAt(created).expiresAt(created.plusSeconds(3600)).build();
    }
}
