package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "catalog.analytics")
@Getter
@Setter
public class AnalyticsProperties implements ClientLimitProperties {
    private ZoneId zone = ZoneId.of("Asia/Tashkent");
    private Duration retention = Duration.ofDays(730);
    private int defaultPeriodDays = 30;
    private int maxPeriodDays = 366;
    private int perClientLimit = 120;
    private Duration perClientWindow = Duration.ofMinutes(10);
    private int globalLimit = 20_000;
    private Duration globalWindow = Duration.ofHours(1);
    private int maxTrackedClients = 20_000;
}
