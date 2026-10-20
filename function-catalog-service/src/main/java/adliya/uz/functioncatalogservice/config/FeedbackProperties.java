package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "catalog.feedback")
@Getter
@Setter
public class FeedbackProperties implements ClientLimitProperties {
    private int perClientLimit = 30;
    private Duration perClientWindow = Duration.ofMinutes(10);
    private int globalLimit = 5_000;
    private Duration globalWindow = Duration.ofHours(1);
    private int maxTrackedClients = 20_000;
}
