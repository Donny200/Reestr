package adliya.uz.task1.service;

import adliya.uz.task1.config.LoginProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Per-client-address limit on password attempts (same fixed-window design as the MFA and reset limiters). */
@Component
public class LoginRateLimiter extends FixedWindowRateLimiter {

    @Autowired
    public LoginRateLimiter(LoginProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginRateLimiter(LoginProperties properties, Clock clock) {
        super(Limits.of(properties), clock);
    }
}
