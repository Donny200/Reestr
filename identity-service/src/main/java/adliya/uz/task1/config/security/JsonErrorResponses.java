package adliya.uz.task1.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Error bodies written by the security filter chain, before any controller advice can run. */
public final class JsonErrorResponses {

    private JsonErrorResponses() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}"
                .formatted(status.value(), status.getReasonPhrase(), message));
    }
}
