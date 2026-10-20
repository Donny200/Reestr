package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.HelpProperties;
import adliya.uz.functioncatalogservice.entity.HelpGuest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Abuse limits of the support chat, each an independent sliding window: per visitor address (never stored, only a
 * salted hash in memory), per support identity, and recovery-code attempts per address.
 */
@Component
public class SupportRateLimiters {

    private final ClientRateLimiter perGuest;
    private final ClientRateLimiter recovery;

    @Autowired
    public SupportRateLimiters(HelpProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SupportRateLimiters(HelpProperties properties, Clock clock) {
        perGuest = new ClientRateLimiter(new ClientRateLimiter.Limits(properties.getPerGuestLimit(), properties.getPerGuestWindow(),
                Integer.MAX_VALUE, properties.getGlobalWindow(), properties.getMaxTrackedClients()), clock);
        recovery = new ClientRateLimiter(new ClientRateLimiter.Limits(properties.getRecoveryAttemptLimit(),
                properties.getRecoveryAttemptWindow(), properties.getGlobalLimit(), properties.getGlobalWindow(),
                properties.getMaxTrackedClients()), clock);
    }

    public void guest(HelpGuest guest) {
        perGuest.acquire("guest:" + guest.getId() + ":" + guest.getCreatedAt().toEpochMilli());
    }

    public void recovery(String clientAddress) {
        recovery.acquire(clientAddress);
    }
}
