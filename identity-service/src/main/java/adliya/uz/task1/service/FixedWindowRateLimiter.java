package adliya.uz.task1.service;

import adliya.uz.task1.config.ClientRateLimitProperties;
import adliya.uz.task1.exception.RateLimitExceededException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Fixed-window limiter keyed by a salted hash of the client key; client keys themselves are never stored. */
public class FixedWindowRateLimiter {

    private final Limits limits;
    private final Clock clock;
    private final byte[] salt = SecureTokens.randomSalt();
    private final Map<String, Window> clients = new HashMap<>();
    private Window global;

    public FixedWindowRateLimiter(Limits limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
    }

    public synchronized void acquire(String clientKey) {
        Instant now = clock.instant();
        String key = SecureTokens.saltedSha256Hex(salt, String.valueOf(clientKey));
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

    public record Limits(int perClient, Duration perClientWindow, int global, Duration globalWindow, int maxTrackedClients) {
        public static Limits of(ClientRateLimitProperties properties) {
            return new Limits(properties.getPerClientLimit(), properties.getPerClientWindow(), properties.getGlobalLimit(),
                    properties.getGlobalWindow(), properties.getMaxTrackedClients());
        }
    }

    private record Window(Instant end, int count) {
        Window increment() { return new Window(end, count + 1); }
        Duration remaining(Instant now) { return Duration.between(now, end); }
    }
}
