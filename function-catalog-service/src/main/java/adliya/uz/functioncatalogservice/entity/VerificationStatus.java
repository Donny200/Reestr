package adliya.uz.functioncatalogservice.entity;

import java.time.Duration;
import java.time.Instant;

public enum VerificationStatus {
    UNVERIFIED, VERIFIED, DUE, OUTDATED;

    public static final Duration RECHECK_AFTER = Duration.ofDays(180);

    public static VerificationStatus of(Instant lastVerifiedAt, boolean outdated, Instant now) {
        if (lastVerifiedAt == null) {
            return UNVERIFIED;
        }
        if (outdated) {
            return OUTDATED;
        }
        return lastVerifiedAt.plus(RECHECK_AFTER).isBefore(now) ? DUE : VERIFIED;
    }
}
