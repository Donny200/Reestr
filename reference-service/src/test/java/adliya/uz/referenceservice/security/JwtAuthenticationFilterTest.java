package adliya.uz.referenceservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "reference-filter-test-secret-that-is-at-least-32-bytes";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new SimpleJwtService(SECRET));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convertsPermissionsClaimToGrantedAuthorities() throws Exception {
        String token = createToken(List.of("TRANSLATION_KEYS_VIEW", "TRANSLATIONS_EDIT"), true);

        AtomicBoolean chainInvoked = invokeFilter(token);

        assertThat(chainInvoked).isTrue();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_CUSTOM_TRANSLATOR",
                        "TRANSLATION_KEYS_VIEW",
                        "TRANSLATIONS_EDIT"
                );

        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        assertThat(principal.email()).isEqualTo("translator@example.com");
        assertThat(principal.role()).isEqualTo("ROLE_CUSTOM_TRANSLATOR");
        assertThat(principal.permissions())
                .containsExactly("TRANSLATION_KEYS_VIEW", "TRANSLATIONS_EDIT");
    }

    @Test
    void acceptsLegacyTokenWithoutPermissionsClaim() throws Exception {
        String token = createToken(List.of(), false);

        AtomicBoolean chainInvoked = invokeFilter(token);

        assertThat(chainInvoked).isTrue();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOM_TRANSLATOR");

        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        assertThat(principal.permissions()).isEmpty();
    }

    private AtomicBoolean invokeFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/translation-keys");
        request.setCookies(new Cookie("accessToken", token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));
        return chainInvoked;
    }

    private String createToken(List<String> permissions, boolean includePermissionsClaim) {
        var builder = Jwts.builder()
                .subject("translator@example.com")
                .claim("role", "ROLE_CUSTOM_TRANSLATOR")
                .claim("mustChangePassword", false);

        if (includePermissionsClaim) {
            builder.claim("permissions", permissions);
        }

        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
