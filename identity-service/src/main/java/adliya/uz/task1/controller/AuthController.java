package adliya.uz.task1.controller;

import adliya.uz.task1.config.LoginProperties;
import adliya.uz.task1.config.MfaProperties;
import adliya.uz.task1.config.security.SessionCookies;
import adliya.uz.task1.dto.ChangePasswordRequest;
import adliya.uz.task1.dto.LoginOutcome;
import adliya.uz.task1.dto.LoginRequest;
import adliya.uz.task1.dto.SessionResponse;
import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.entity.MfaChallenge;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.InvalidRefreshTokenException;
import adliya.uz.task1.service.AuthService;
import adliya.uz.task1.service.LoginAttemptGuard;
import adliya.uz.task1.service.LoginRateLimiter;
import adliya.uz.task1.service.MfaService;
import adliya.uz.task1.service.RefreshTokenService;
import adliya.uz.task1.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionCookies sessionCookies;
    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final MfaProperties mfaProperties;
    private final LoginProperties loginProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final LoginAttemptGuard loginAttemptGuard;

    /**
     * Password step. Users with a second factor (or who must enroll one) receive 202 and only a short-lived challenge
     * cookie scoped to the MFA endpoints; no access or refresh token exists until the second factor is completed.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        loginRateLimiter.acquire(ClientAddresses.of(http, loginProperties.getClientAddressHeader()));
        loginAttemptGuard.check(request.getEmail());
        User user;
        try {
            user = authService.login(request);
        } catch (AuthenticationException exception) {
            loginAttemptGuard.recordFailure(request.getEmail());
            throw exception;
        }
        loginAttemptGuard.recordSuccess(request.getEmail());
        MfaService.Decision decision = mfaService.decide(user);
        if (decision != MfaService.Decision.FULL_SESSION) {
            MfaChallenge.Purpose purpose = decision == MfaService.Decision.ENROLL ? MfaChallenge.Purpose.ENROLL : MfaChallenge.Purpose.VERIFY;
            MfaService.IssuedChallenge challenge = mfaService.createChallenge(user, purpose);
            return sessionCookies.clear(HttpStatus.ACCEPTED)
                    .header(HttpHeaders.SET_COOKIE, sessionCookies.challengeCookie(challenge.rawToken(), mfaProperties.getChallengeTtl()).toString())
                    .body(new LoginOutcome(true, purpose == MfaChallenge.Purpose.ENROLL, challenge.expiresAt()));
        }
        return sessionCookies.start(user, http).body(withMfa(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = "${app.cookie.refresh-token-name}", required = false) String refreshToken) {
        if (refreshToken == null) {
            return sessionCookies.clear(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshToken);
            return sessionCookies.refreshed(result).build();
        } catch (InvalidRefreshTokenException ex) {
            return sessionCookies.clear(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.cookie.refresh-token-name}", required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        return sessionCookies.clear(HttpStatus.OK).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(withMfa(authService.getCurrentUserEntity()));
    }

    /** Changing the password ends every other session; the current browser receives a fresh one. */
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        User user = authService.changePassword(request);
        sessionService.revokeAll(user.getId(), SessionService.REASON_PASSWORD);
        return sessionCookies.start(user, http).body("Password changed successfully.");
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(HttpServletRequest http) {
        return sessionService.ownSessions(sessionCookies.currentSessionId(http));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId, HttpServletRequest http) {
        String current = sessionCookies.currentSessionId(http);
        sessionService.revokeOwn(sessionId, current);
        return sessionId.equals(current) ? sessionCookies.clear(HttpStatus.NO_CONTENT).build() : ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    public Map<String, Integer> revokeOtherSessions(HttpServletRequest http) {
        return Map.of("revoked", sessionService.revokeOwnOthers(sessionCookies.currentSessionId(http)));
    }

    private UserResponse withMfa(User user) {
        return UserResponse.from(user).withMfa(mfaService.summary(user));
    }
}
