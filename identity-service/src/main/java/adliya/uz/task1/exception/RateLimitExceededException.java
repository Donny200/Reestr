package adliya.uz.task1.exception;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {
    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many attempts; retry after " + retryAfter.toSeconds() + " seconds");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
