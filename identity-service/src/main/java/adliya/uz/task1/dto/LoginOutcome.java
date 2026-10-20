package adliya.uz.task1.dto;

import java.time.Instant;

/**
 * Password step accepted but a second factor is pending. No access or refresh token exists yet; the browser only
 * holds a short-lived challenge cookie scoped to the MFA endpoints.
 */
public record LoginOutcome(boolean mfaRequired, boolean enrollmentRequired, Instant expiresAt) {}
