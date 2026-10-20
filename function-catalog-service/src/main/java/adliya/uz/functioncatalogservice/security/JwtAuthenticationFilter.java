package adliya.uz.functioncatalogservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SimpleJwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = getTokenFromCookie(request);

        if (token != null) {
            try {
                Claims claims = jwtService.parseClaims(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                @SuppressWarnings("unchecked")
                List<Object> rawOrgIds = claims.get("organizationIds", List.class);
                List<Long> organizationIds = rawOrgIds == null
                        ? List.of()
                        : rawOrgIds.stream().map(o -> Long.valueOf(o.toString())).toList();

                @SuppressWarnings("unchecked")
                List<Object> rawPermissions = claims.get("permissions", List.class);
                List<String> permissions = rawPermissions == null
                        ? List.of()
                        : rawPermissions.stream().map(Object::toString).toList();

                JwtPrincipal principal = new JwtPrincipal(email, role, organizationIds, permissions, claims.get("userId", Long.class));

                List<SimpleGrantedAuthority> authorities = Stream.concat(
                                Stream.of(role),
                                permissions.stream()
                        )
                        .distinct()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
