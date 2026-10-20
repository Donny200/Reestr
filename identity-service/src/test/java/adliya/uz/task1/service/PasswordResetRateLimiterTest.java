package adliya.uz.task1.service;

import adliya.uz.task1.config.PasswordResetProperties;
import adliya.uz.task1.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetRateLimiterTest {

    @Test
    void perClientLimitIsEnforcedWithinTheWindowAndReleasedAfterIt() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setPerClientLimit(2);
        properties.setPerClientWindow(Duration.ofMinutes(15));
        properties.setGlobalLimit(100);
        MutableClock clock = new MutableClock(Instant.parse("2026-10-12T09:00:00Z"));
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(properties, clock);

        limiter.acquire("203.0.113.9");
        limiter.acquire("203.0.113.9");
        assertThatThrownBy(() -> limiter.acquire("203.0.113.9"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(error -> assertThat(((RateLimitExceededException) error).retryAfter()).isEqualTo(Duration.ofMinutes(15)));
        assertThatCode(() -> limiter.acquire("203.0.113.10")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatCode(() -> limiter.acquire("203.0.113.9")).doesNotThrowAnyException();
    }

    @Test
    void globalLimitProtectsAgainstDistributedAttempts() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setPerClientLimit(10);
        properties.setGlobalLimit(3);
        properties.setGlobalWindow(Duration.ofHours(1));
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        limiter.acquire("a");
        limiter.acquire("b");
        limiter.acquire("c");
        assertThatThrownBy(() -> limiter.acquire("d")).isInstanceOf(RateLimitExceededException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
