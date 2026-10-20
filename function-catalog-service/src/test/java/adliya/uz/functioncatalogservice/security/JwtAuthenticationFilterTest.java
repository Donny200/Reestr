package adliya.uz.functioncatalogservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private SimpleJwtService jwtService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void turnsPermissionClaimIntoPrincipalAndAuthorities() throws Exception {
        Claims claims = claims(List.of("FUNCTIONS_EDIT", "FUNCTIONS_MANAGE_ANY_ORGANIZATION"), true);
        when(jwtService.parseClaims("valid-token")).thenReturn(claims);
        MockHttpServletRequest request = requestWithToken("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(JwtPrincipal.class);
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.organizationIds()).containsExactly(10L, 20L);
        assertThat(principal.permissions())
                .containsExactly("FUNCTIONS_EDIT", "FUNCTIONS_MANAGE_ANY_ORGANIZATION");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder(
                        "ROLE_CUSTOM_ADMIN",
                        "FUNCTIONS_EDIT",
                        "FUNCTIONS_MANAGE_ANY_ORGANIZATION"
                );
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingPermissionClaimIsBackwardCompatible() throws Exception {
        Claims claims = claims(List.of(), false);
        when(jwtService.parseClaims("old-token")).thenReturn(claims);
        MockHttpServletRequest request = requestWithToken("old-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        assertThat(principal.userId()).isNull();
        assertThat(principal.permissions()).isEmpty();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOM_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    private Claims claims(List<String> permissions, boolean includePermissions) {
        var builder = Jwts.claims()
                .subject("admin@example.com")
                .add("role", "ROLE_CUSTOM_ADMIN")
                .add("organizationIds", List.of(10L, 20L));
        if (includePermissions) {
            builder.add("permissions", permissions);
            builder.add("userId", 42L);
        }
        return builder.build();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/functions");
        request.setCookies(new Cookie("accessToken", token));
        return request;
    }
}
