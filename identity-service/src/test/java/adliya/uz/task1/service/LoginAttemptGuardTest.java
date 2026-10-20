package adliya.uz.task1.service;

import adliya.uz.task1.config.LoginProperties;
import adliya.uz.task1.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptGuardTest {

    private static final Instant START = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void lockedAfterConfiguredFailuresUntilTheWindowEndsAndClearedBySuccess() {
        LoginProperties properties = new LoginProperties();
        properties.setPerAccountLimit(3);
        properties.setPerAccountWindow(Duration.ofMinutes(15));
        MutableClock clock = new MutableClock(START);
        LoginAttemptGuard guard = new LoginAttemptGuard(properties, clock);

        for (int i = 0; i < 3; i++) {
            guard.check("Admin@Example.test");
            guard.recordFailure(" admin@example.test ");
        }
        // Same account in any spelling is locked; another account is not.
        assertThatThrownBy(() -> guard.check("ADMIN@example.test")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> guard.check("other@example.test")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> guard.check("admin@example.test")).doesNotThrowAnyException();

        guard.recordFailure("admin@example.test");
        guard.recordFailure("admin@example.test");
        guard.recordSuccess("admin@example.test");
        guard.recordFailure("admin@example.test");
        assertThatCode(() -> guard.check("admin@example.test")).doesNotThrowAnyException();
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
