package adliya.uz.task1.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class MandatoryPasswordChangeFilter extends OncePerRequestFilter {

    private static final Set<AllowedRequest> ALLOWED_REQUESTS = Set.of(
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/login"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/change-password"),
            new AllowedRequest(HttpMethod.GET.name(), "/api/auth/password-reset/options"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/password-reset/request"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/password-reset/complete"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/logout"),
            new AllowedRequest(HttpMethod.GET.name(), "/api/auth/me"),
            new AllowedRequest(HttpMethod.GET.name(), "/api/auth/mfa"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/mfa/enroll"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/mfa/confirm"),
            new AllowedRequest(HttpMethod.POST.name(), "/api/auth/mfa/verify")
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (requiresPasswordChange(authentication) && !isAllowed(request)) {
            JsonErrorResponses.write(response, HttpStatus.FORBIDDEN, "Password must be changed before accessing this resource");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresPasswordChange(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserPrincipal principal
                && principal.mustChangePassword();
    }

    private boolean isAllowed(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED_REQUESTS.contains(new AllowedRequest(request.getMethod(), requestPath));
    }

    private record AllowedRequest(String method, String path) {
    }
}
