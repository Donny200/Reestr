package adliya.uz.task1.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public ResponseCookie createAccessCookie(String token) {
        return build(cookieProperties.getAccessTokenName(), token, "/",
                Duration.ofMillis(jwtProperties.getExpiration()));
    }

    public ResponseCookie createRefreshCookie(String token) {
        return build(cookieProperties.getRefreshTokenName(), token, cookieProperties.getRefreshTokenPath(),
                Duration.ofMillis(jwtProperties.getRefreshExpiration()));
    }

    public ResponseCookie createLogoutAccessCookie() {
        return build(cookieProperties.getAccessTokenName(), "", "/", Duration.ZERO);
    }

    /** Pending-MFA proof: scoped to the MFA endpoints only, so it cannot reach any other API. */
    public ResponseCookie createMfaChallengeCookie(String token, Duration maxAge) {
        return build(cookieProperties.getMfaChallengeName(), token, cookieProperties.getMfaChallengePath(), maxAge);
    }

    public ResponseCookie createLogoutMfaChallengeCookie() {
        return build(cookieProperties.getMfaChallengeName(), "", cookieProperties.getMfaChallengePath(), Duration.ZERO);
    }

    public ResponseCookie createLogoutRefreshCookie() {
        return build(cookieProperties.getRefreshTokenName(), "", cookieProperties.getRefreshTokenPath(), Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}