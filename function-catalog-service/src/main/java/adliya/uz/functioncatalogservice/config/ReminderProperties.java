package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "catalog.reminders")
@Getter
@Setter
public class ReminderProperties {
    private Duration renotifyAfter = Duration.ofDays(14);
}
