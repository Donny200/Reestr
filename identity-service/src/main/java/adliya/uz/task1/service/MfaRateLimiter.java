package adliya.uz.task1.service;

import adliya.uz.task1.config.MfaProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Limits code and recovery attempts per client address and per account. */
@Component
public class MfaRateLimiter extends FixedWindowRateLimiter {

    @Autowired
    public MfaRateLimiter(MfaProperties properties) {
        this(properties, Clock.systemUTC());
    }

    MfaRateLimiter(MfaProperties properties, Clock clock) {
        super(Limits.of(properties), clock);
    }
}
