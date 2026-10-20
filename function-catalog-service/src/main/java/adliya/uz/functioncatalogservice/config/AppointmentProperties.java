package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "catalog.appointments")
@Getter
@Setter
public class AppointmentProperties implements ClientLimitProperties {
    /** Time zone of a new organization schedule. */
    private String defaultTimeZone = "Asia/Tashkent";
    /** New bookings per visitor address and window (addresses are never stored, only a salted hash in memory). */
    private int perClientLimit = 10;
    private Duration perClientWindow = Duration.ofMinutes(15);
    private int globalLimit = 2_000;
    private Duration globalWindow = Duration.ofHours(1);
    /** Look-ups, cancellations and reschedules per visitor address and window. */
    private int lookupLimit = 60;
    private Duration lookupWindow = Duration.ofMinutes(10);
    private int maxTrackedClients = 10_000;
    /** Upcoming bookings one phone number or e-mail address may hold at one organization. */
    private int maxUpcomingPerContact = 3;
    /** Days of availability returned by one request. */
    private int maxDaysPerRequest = 31;
    /** Visitor name, phone and e-mail are removed this long after the appointment ended. */
    private Duration contactRetention = Duration.ofDays(30);
}
