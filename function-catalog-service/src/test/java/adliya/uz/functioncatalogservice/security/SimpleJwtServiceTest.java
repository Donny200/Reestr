package adliya.uz.functioncatalogservice.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleJwtServiceTest {

    private static final String SECRET =
            "function-catalog-test-secret-that-is-long-enough-for-hs256";
    private static final String OTHER_SECRET =
            "different-function-catalog-secret-long-enough-for-hs256";

    private static SimpleJwtService service;

    @BeforeAll
    static void setUp() {
        service = new SimpleJwtService(SECRET);
    }

    @Test
    void validatesHmacTokenAndReturnsAllClaims() {
        String token = token(false, true, SECRET);

        var claims = service.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("admin@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_CUSTOM_ADMIN");
        assertThat(claims.get("organizationIds", List.class)).containsExactly(10, 20);
        assertThat(claims.get("permissions", List.class))
                .containsExactly("FUNCTIONS_EDIT", "FUNCTIONS_MANAGE_ANY_ORGANIZATION");
        assertThat(claims.get("mustChangePassword", Boolean.class)).isFalse();
    }

    @Test
    void rejectsTokenUntilMandatoryPasswordChangeIsComplete() {
        String token = token(true, true, SECRET);

        assertThatThrownBy(() -> service.parseClaims(token))
                .isInstanceOf(UnsupportedJwtException.class)
                .hasMessageContaining("password has been changed");
    }

    @Test
    void rejectsTokenWithoutMandatoryPasswordChangeClaim() {
        String token = token(false, false, SECRET);

        assertThatThrownBy(() -> service.parseClaims(token))
                .isInstanceOf(UnsupportedJwtException.class)
                .hasMessageContaining("password has been changed");
    }

    @Test
    void rejectsTokenSignedWithAnotherHmacSecret() {
        String token = token(false, true, OTHER_SECRET);

        assertThatThrownBy(() -> service.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }

    private static String token(boolean mustChangePassword, boolean includePasswordClaim, String secret) {
        var builder = Jwts.builder()
                .subject("admin@example.com")
                .claim("role", "ROLE_CUSTOM_ADMIN")
                .claim("organizationIds", List.of(10L, 20L))
                .claim("permissions", List.of(
                        "FUNCTIONS_EDIT",
                        "FUNCTIONS_MANAGE_ANY_ORGANIZATION"
                ));

        if (includePasswordClaim) {
            builder.claim("mustChangePassword", mustChangePassword);
        }

        return builder
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
