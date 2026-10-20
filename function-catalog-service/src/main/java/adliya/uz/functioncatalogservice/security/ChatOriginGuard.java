package adliya.uz.functioncatalogservice.security;

import adliya.uz.functioncatalogservice.config.HelpProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Cross-site request protection for the cookie-authenticated support endpoints, on top of the SameSite=Strict guest
 * cookie: a browser request that names a foreign origin is refused before anything is read or written. Requests
 * without an Origin header (same-origin GETs, non-browser clients) carry no third-party ambient cookies and pass.
 */
@Component
@RequiredArgsConstructor
public class ChatOriginGuard {

    private final HelpProperties properties;

    public void check(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin) && !allowedOrigins().contains(normalize(origin))) {
            throw new AccessDeniedException("Requests from this origin are not accepted");
        }
    }

    Set<String> allowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        properties.getAllowedOrigins().stream().filter(StringUtils::hasText).map(ChatOriginGuard::normalize).forEach(origins::add);
        if (StringUtils.hasText(properties.getPublicBaseUrl())) {
            origins.add(normalize(properties.getPublicBaseUrl()));
        }
        return origins;
    }

    private static String normalize(String origin) {
        return origin.trim().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }
}
