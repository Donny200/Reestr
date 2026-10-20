package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.*;

import java.time.Instant;

public record ReportResponse(
        Long id, ReportEntityType entityType, Long entityId, String entityLabel, Long organizationId,
        ReportCategory category, String description, String contact, boolean contactAvailable, String language,
        ReportStatus status, String resolutionNote, Long handledByUserId, Instant createdAt, Instant updatedAt,
        Boolean serviceChangedSinceReport
) {
    public static ReportResponse of(InformationReport report, boolean revealContact, Boolean serviceChangedSinceReport) {
        return new ReportResponse(report.getId(), report.getEntityType(), report.getEntityId(), report.getEntityLabel(),
                report.getOrganizationId(), report.getCategory(), report.getDescription(),
                revealContact ? report.getContact() : null, report.getContact() != null,
                report.getLanguage(), report.getStatus(), report.getResolutionNote(), report.getHandledByUserId(),
                report.getCreatedAt(), report.getUpdatedAt(), serviceChangedSinceReport);
    }
}
