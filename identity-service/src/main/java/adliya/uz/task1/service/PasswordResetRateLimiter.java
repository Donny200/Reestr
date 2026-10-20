package adliya.uz.task1.service;

import adliya.uz.task1.config.PasswordResetProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Limits anonymous reset requests and token attempts per client address and in total. */
@Component
public class PasswordResetRateLimiter extends FixedWindowRateLimiter {

    @Autowired
    public PasswordResetRateLimiter(PasswordResetProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PasswordResetRateLimiter(PasswordResetProperties properties, Clock clock) {
        super(Limits.of(properties), clock);
    }
}
