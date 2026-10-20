package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AppointmentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Abuse limits of public appointment endpoints per visitor address (only a salted hash is kept, in memory). */
@Component
public class AppointmentRateLimiters {

    private final ClientRateLimiter bookings;
    private final ClientRateLimiter lookups;

    @Autowired
    public AppointmentRateLimiters(AppointmentProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AppointmentRateLimiters(AppointmentProperties properties, Clock clock) {
        bookings = new ClientRateLimiter(ClientRateLimiter.Limits.of(properties), clock);
        lookups = new ClientRateLimiter(new ClientRateLimiter.Limits(properties.getLookupLimit(), properties.getLookupWindow(),
                Integer.MAX_VALUE, properties.getGlobalWindow(), properties.getMaxTrackedClients()), clock);
    }

    public void booking(String clientAddress) {
        bookings.acquire(clientAddress);
    }

    public void lookup(String clientAddress) {
        lookups.acquire(clientAddress);
    }
}
