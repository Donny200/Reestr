package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Background machine-translation queue (catalog.translation.*). Provider credentials live under azure.translator.*. */
@Component
@ConfigurationProperties(prefix = "catalog.translation")
@Getter
@Setter
public class MachineTranslationProperties {
    /** Master switch for the background worker; content is still queued so it can be processed later. */
    private boolean enabled = true;
    /** Jobs handled per worker tick; each job is one provider request per distinct set of missing languages. */
    private int batchSize = 5;
    /** Delay before the first tick after start-up (no provider calls happen during start-up itself). */
    private Duration initialDelay = Duration.ofSeconds(30);
    /** Pause between ticks. */
    private Duration interval = Duration.ofSeconds(20);
    /** Attempts before a job is marked FAILED and left for an explicit retry. */
    private int maxAttempts = 8;
    /** How long a claimed job stays RUNNING before another tick may pick it up again. */
    private Duration lease = Duration.ofMinutes(5);
    /** Upper bound for one explicit backfill request. */
    private int backfillMaxLimit = 200;
}
