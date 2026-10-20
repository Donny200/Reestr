package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "catalog.reports")
@Getter
@Setter
public class ReportProperties implements ClientLimitProperties {
    private String clientAddressHeader = "X-Real-IP";
    private int perClientLimit = 5;
    private Duration perClientWindow = Duration.ofMinutes(15);
    private int globalLimit = 200;
    private Duration globalWindow = Duration.ofHours(1);
    private int perEntityDailyLimit = 30;
    private int maxTrackedClients = 10_000;
}
