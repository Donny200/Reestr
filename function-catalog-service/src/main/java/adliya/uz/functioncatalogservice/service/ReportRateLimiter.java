package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.ReportProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class ReportRateLimiter extends ClientRateLimiter {

    @Autowired
    public ReportRateLimiter(ReportProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ReportRateLimiter(ReportProperties properties, Clock clock) {
        super(Limits.of(properties), clock);
    }
}
