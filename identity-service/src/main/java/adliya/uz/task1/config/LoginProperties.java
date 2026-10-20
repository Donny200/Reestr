package adliya.uz.task1.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Brute-force protection of the password step (app.login.*). Counters are in memory per instance. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.login")
public class LoginProperties implements ClientRateLimitProperties {
    /** Header carrying the visitor address when the service sits behind nginx / the gateway. */
    private String clientAddressHeader = "X-Real-IP";
    /** Password attempts (successful or not) accepted per client address within perClientWindow. */
    private int perClientLimit = 20;
    private Duration perClientWindow = Duration.ofMinutes(15);
    /** Failed attempts per submitted e-mail address before further attempts are refused until the window ends. */
    private int perAccountLimit = 10;
    private Duration perAccountWindow = Duration.ofMinutes(15);
    private int globalLimit = 2_000;
    private Duration globalWindow = Duration.ofHours(1);
    private int maxTrackedClients = 10_000;
}
