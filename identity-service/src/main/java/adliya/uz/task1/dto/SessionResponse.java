package adliya.uz.task1.dto;

import java.time.Instant;

/** One active browser session. Carries no token material. */
public record SessionResponse(String sessionId, Instant createdAt, Instant lastUsedAt, Instant expiresAt, String userAgent, boolean current) {}
