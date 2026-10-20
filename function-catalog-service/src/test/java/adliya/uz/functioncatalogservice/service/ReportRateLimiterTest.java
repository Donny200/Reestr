package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.ReportProperties;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-09T10:00:00Z"));

    @Test void limitsEachClientWithinItsWindowAndResetsAfterwards() {
        var limiter = new ReportRateLimiter(properties(2, 100, 10), clock);
        limiter.acquire("203.0.113.5");
        limiter.acquire("203.0.113.5");
        assertThatThrownBy(() -> limiter.acquire("203.0.113.5"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfter()).isEqualTo(Duration.ofMinutes(15)));
        assertThatCode(() -> limiter.acquire("198.51.100.7")).doesNotThrowAnyException();
        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.acquire("203.0.113.5")).doesNotThrowAnyException();
    }

    @Test void globalLimitProtectsAgainstRotatingAddresses() {
        var limiter = new ReportRateLimiter(properties(5, 3, 10), clock);
        limiter.acquire("a");
        limiter.acquire("b");
        limiter.acquire("c");
        assertThatThrownBy(() -> limiter.acquire("d")).isInstanceOf(RateLimitExceededException.class);
        clock.advance(Duration.ofHours(1));
        assertThatCode(() -> limiter.acquire("d")).doesNotThrowAnyException();
    }

    @Test void trackingCapacityFailsClosedUntilWindowsExpire() {
        var limiter = new ReportRateLimiter(properties(5, 100, 2), clock);
        limiter.acquire("a");
        limiter.acquire("b");
        assertThatThrownBy(() -> limiter.acquire("c")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.acquire("a")).doesNotThrowAnyException();
        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> limiter.acquire("c")).doesNotThrowAnyException();
    }

    private static ReportProperties properties(int perClient, int global, int tracked) {
        var properties = new ReportProperties();
        properties.setPerClientLimit(perClient);
        properties.setGlobalLimit(global);
        properties.setMaxTrackedClients(tracked);
        return properties;
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
