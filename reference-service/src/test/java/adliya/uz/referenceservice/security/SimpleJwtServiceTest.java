package adliya.uz.referenceservice.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleJwtServiceTest {

    private static final String SECRET = "reference-service-test-secret-that-is-at-least-32-bytes";
    private static final String OTHER_SECRET = "different-reference-test-secret-that-is-at-least-32-bytes";

    private final SimpleJwtService service = new SimpleJwtService(SECRET);

    @Test
    void validatesHmacTokenUsingSharedSecret() {
        String token = token(secretKey(SECRET), false, true);

        var claims = service.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("admin@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_SUPER_ADMIN");
        assertThat(claims.get("permissions")).isEqualTo(List.of("TRANSLATIONS_EDIT"));
        assertThat(claims.get("mustChangePassword", Boolean.class)).isFalse();
    }

    @Test
    void rejectsTokenUntilMandatoryPasswordChangeIsComplete() {
        String token = token(secretKey(SECRET), true, true);

        assertThatThrownBy(() -> service.parseClaims(token))
                .isInstanceOf(UnsupportedJwtException.class)
                .hasMessageContaining("password has been changed");
    }

    @Test
    void rejectsTokenWithoutMandatoryPasswordChangeClaim() {
        String token = token(secretKey(SECRET), false, false);

        assertThatThrownBy(() -> service.parseClaims(token))
                .isInstanceOf(UnsupportedJwtException.class)
                .hasMessageContaining("password has been changed");
    }

    @Test
    void rejectsTokenSignedWithAnotherHmacSecret() {
        String token = token(secretKey(OTHER_SECRET), false, true);

        assertThatThrownBy(() -> service.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }

    private String token(SecretKey signingKey, boolean mustChangePassword, boolean includePasswordClaim) {
        var builder = Jwts.builder()
                .subject("admin@example.com")
                .claim("role", "ROLE_SUPER_ADMIN")
                .claim("permissions", List.of("TRANSLATIONS_EDIT"));

        if (includePasswordClaim) {
            builder.claim("mustChangePassword", mustChangePassword);
        }

        return builder.signWith(signingKey).compact();
    }

    private SecretKey secretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
