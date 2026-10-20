package adliya.uz.task1.dto;

import java.time.Instant;

/**
 * Returned once to the SUPER_ADMIN who initiated a reset. The path contains the raw single-use token; it is not
 * stored anywhere else and cannot be retrieved again.
 */
public record PasswordResetLinkResponse(Long userId, String resetPath, Instant expiresAt) {}
