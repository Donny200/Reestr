package adliya.uz.task1.config.security;

import adliya.uz.task1.entity.User;
import adliya.uz.task1.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Issues and clears the access/refresh cookie pair; the only place a full session is started. */
@Component
@RequiredArgsConstructor
public class SessionCookies {

    private final CookieUtil cookieUtil;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CookieProperties cookieProperties;

    /** Full session unless the password must be changed first, in which case only a restricted access token is issued. */
    public ResponseEntity.BodyBuilder start(User user, HttpServletRequest request) {
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.createAccessCookie(jwtService.generateToken(user)).toString())
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.createLogoutRefreshCookie().toString());
        }
        RefreshTokenService.IssuedSession session = refreshTokenService.createSession(user, userAgent(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createAccessCookie(jwtService.generateToken(user, session.sessionId())).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshCookie(session.rawToken()).toString());
    }

    /** Rotated refresh token plus a new access token bound to the same session. */
    public ResponseEntity.BodyBuilder refreshed(RefreshTokenService.RotationResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createAccessCookie(jwtService.generateToken(result.user(), result.sessionId())).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshCookie(result.rawToken()).toString());
    }

    public ResponseEntity.BodyBuilder clear(HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createLogoutAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createLogoutRefreshCookie().toString());
    }

    public ResponseCookie challengeCookie(String rawToken, java.time.Duration maxAge) {
        return cookieUtil.createMfaChallengeCookie(rawToken, maxAge);
    }

    public ResponseCookie clearChallengeCookie() {
        return cookieUtil.createLogoutMfaChallengeCookie();
    }

    /** Session id carried by the current access token, or null for restricted or legacy tokens. */
    public String currentSessionId(HttpServletRequest request) {
        String token = cookie(request, cookieProperties.getAccessTokenName());
        if (token == null) {
            return null;
        }
        try {
            return jwtService.extractClaim(token, JwtService::sessionId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public String challengeToken(HttpServletRequest request) {
        return cookie(request, cookieProperties.getMfaChallengeName());
    }

    public static String userAgent(HttpServletRequest request) {
        String header = request == null ? null : request.getHeader(HttpHeaders.USER_AGENT);
        if (header == null || header.isBlank()) {
            return null;
        }
        String cleaned = header.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(0, 255);
    }

    static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }
}
