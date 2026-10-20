package adliya.uz.task1.dto;

import java.time.Instant;

/** Enrollment state of a user as shown to the user and to SUPER_ADMIN. Never includes the secret or codes. */
public record MfaStatusResponse(boolean enabled, boolean required, boolean enrolled, Instant confirmedAt,
                                Instant enrollmentDeadline, boolean enrollmentOverdue, long recoveryCodesRemaining) {}
