package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.HelpProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class HelpRateLimiter extends ClientRateLimiter {

    @Autowired
    public HelpRateLimiter(HelpProperties properties) {
        this(properties, Clock.systemUTC());
    }

    HelpRateLimiter(HelpProperties properties, Clock clock) {
        super(Limits.of(properties), clock);
    }
}
