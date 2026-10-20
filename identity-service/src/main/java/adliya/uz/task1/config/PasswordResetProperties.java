package adliya.uz.task1.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties implements ClientRateLimitProperties {
    /** Lifetime of a reset token (single use). */
    private Duration tokenTtl = Duration.ofMinutes(60);
    /** Path on the frontend that completes a reset; the raw token is appended as the {@code token} query parameter. */
    private String resetPath = "/reset-password";
    /** Absolute public origin of the frontend (e.g. https://reestr.example). Required for e-mailed links. */
    private String publicBaseUrl = "";
    /** Request header carrying the real client address behind the nginx/gateway proxies. */
    private String clientAddressHeader = "X-Real-IP";
    private int perClientLimit = 5;
    private Duration perClientWindow = Duration.ofMinutes(15);
    private int globalLimit = 100;
    private Duration globalWindow = Duration.ofHours(1);
    private int maxTrackedClients = 10_000;
    private Mail mail = new Mail();

    @Getter
    @Setter
    public static class Mail {
        /** Self-service "Forgot password?" is offered only when this is true and SMTP (spring.mail.host) is configured. */
        private boolean enabled = false;
        private String from = "";
    }
}
