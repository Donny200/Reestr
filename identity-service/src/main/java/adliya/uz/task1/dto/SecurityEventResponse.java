package adliya.uz.task1.dto;

import adliya.uz.task1.entity.SecurityAuditEvent;

import java.time.Instant;

/** Internal security event of a user as shown to SUPER_ADMIN; never contains secrets, codes or tokens. */
public record SecurityEventResponse(Long id, Long userId, Long actorUserId, String action, String details, Instant performedAt) {
    public static SecurityEventResponse from(SecurityAuditEvent event) {
        return new SecurityEventResponse(event.getId(), event.getUserId(), event.getActorUserId(), event.getAction(), event.getDetails(), event.getPerformedAt());
    }
}
