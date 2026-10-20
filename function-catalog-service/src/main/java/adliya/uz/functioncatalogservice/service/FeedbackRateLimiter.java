package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.FeedbackProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class FeedbackRateLimiter extends ClientRateLimiter {

    @Autowired
    public FeedbackRateLimiter(FeedbackProperties properties) {
        this(properties, Clock.systemUTC());
    }

    FeedbackRateLimiter(FeedbackProperties properties, Clock clock) {
        super(Limits.of(properties), clock);
    }
}
