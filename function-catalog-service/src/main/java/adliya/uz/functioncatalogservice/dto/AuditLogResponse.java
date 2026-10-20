package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.*;
import java.time.Instant;

public record AuditLogResponse(Long id, Long performedByUserId, String performedBy,
                               AuditAction action, Instant performedAt, String details) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getPerformedByUserId(), log.getPerformedBy(),
                log.getAction(), log.getPerformedAt(), log.getDetails());
    }
}
