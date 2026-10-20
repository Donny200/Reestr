package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class EngagementRateLimiter extends ClientRateLimiter {

    @Autowired
    public EngagementRateLimiter(AnalyticsProperties properties) {
        this(properties, Clock.systemUTC());
    }

    EngagementRateLimiter(AnalyticsProperties properties, Clock clock) {
        super(Limits.of(properties), clock);
    }
}
