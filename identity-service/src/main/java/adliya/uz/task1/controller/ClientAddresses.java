package adliya.uz.task1.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/** Visitor address used for rate limits: the proxy-supplied header when configured and present, else the peer. */
final class ClientAddresses {

    private ClientAddresses() {
    }

    static String of(HttpServletRequest request, String header) {
        String forwarded = StringUtils.hasText(header) ? request.getHeader(header) : null;
        return StringUtils.hasText(forwarded) ? forwarded.trim() : request.getRemoteAddr();
    }
}
