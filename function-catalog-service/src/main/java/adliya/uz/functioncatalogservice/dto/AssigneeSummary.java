package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.OrgFunction;

import java.time.Instant;

/** Staff member responsible for keeping a card current, as snapshotted at assignment time. */
public record AssigneeSummary(Long userId, String name, String email, Instant assignedAt) {
    public static AssigneeSummary of(OrgFunction function) {
        return function.getAssignedUserId() == null ? null
                : new AssigneeSummary(function.getAssignedUserId(), function.getAssignedUserName(), function.getAssignedUserEmail(),
                function.getAssignedAt());
    }
}
