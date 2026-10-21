package adliya.uz.referenceservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "interface.translation")
@Getter
@Setter
public class InterfaceTranslationProperties {
    /** Master switch; when off, nothing is sent to the provider and queued jobs wait. */
    private boolean enabled = true;
    /** Delay before the first tick after start-up. */
    private Duration initialDelay = Duration.ofMinutes(1);
    /** Pause between worker ticks. */
    private Duration interval = Duration.ofMinutes(2);
    /** How often active languages are compared with the dictionary to queue languages that gained gaps. */
    private Duration scanInterval = Duration.ofMinutes(15);
    /** Language jobs handled per tick. */
    private int batchSize = 2;
    /** Texts per provider request (Azure accepts up to 1000) and characters per request (Azure accepts 50 000). */
    private int textsPerRequest = 100;
    private int charactersPerRequest = 20_000;
    /** Attempts before a job is FAILED and waits for an explicit retry. */
    private int maxAttempts = 6;
    /** How long a claimed job stays RUNNING before another tick may take it over. */
    private Duration lease = Duration.ofMinutes(10);
}
