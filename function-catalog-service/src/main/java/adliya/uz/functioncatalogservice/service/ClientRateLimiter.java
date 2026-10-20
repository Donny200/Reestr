package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.ClientLimitProperties;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ClientRateLimiter {

    private final Limits limits;
    private final Clock clock;
    private final byte[] salt = new byte[16];
    private final Map<String, Window> clients = new HashMap<>();
    private Window global;

    public ClientRateLimiter(Limits limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
        new SecureRandom().nextBytes(salt);
    }

    public synchronized void acquire(String clientAddress) {
        Instant now = clock.instant();
        String key = fingerprint(clientAddress);
        Window existing = clients.get(key);
        Window client = active(existing, now) ? existing : null;
        if (client == null && !hasCapacity(now)) {
            throw new RateLimitExceededException(limits.perClientWindow());
        }
        if (client == null) {
            client = new Window(now.plus(limits.perClientWindow()), 0);
        }
        if (client.count() >= limits.perClient()) {
            throw new RateLimitExceededException(client.remaining(now));
        }
        if (!active(global, now)) {
            global = new Window(now.plus(limits.globalWindow()), 0);
        }
        if (global.count() >= limits.global()) {
            throw new RateLimitExceededException(global.remaining(now));
        }
        clients.put(key, client.increment());
        global = global.increment();
    }

    private boolean hasCapacity(Instant now) {
        if (clients.size() < limits.maxTrackedClients()) {
            return true;
        }
        clients.values().removeIf(window -> !active(window, now));
        return clients.size() < limits.maxTrackedClients();
    }

    private static boolean active(Window window, Instant now) {
        return window != null && window.end().isAfter(now);
    }

    private String fingerprint(String clientAddress) {
        return Sha256.hex(salt, String.valueOf(clientAddress).getBytes(StandardCharsets.UTF_8));
    }

    public record Limits(int perClient, Duration perClientWindow, int global, Duration globalWindow, int maxTrackedClients) {
        public static Limits of(ClientLimitProperties properties) {
            return new Limits(properties.getPerClientLimit(), properties.getPerClientWindow(), properties.getGlobalLimit(),
                    properties.getGlobalWindow(), properties.getMaxTrackedClients());
        }
    }

    private record Window(Instant end, int count) {
        Window increment() { return new Window(end, count + 1); }
        Duration remaining(Instant now) { return Duration.between(now, end); }
    }
}
