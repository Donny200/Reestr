package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.ReportCategory;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.entity.ReportStatus;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record ReportQuery(Set<ReportStatus> statuses, ReportCategory category, ReportEntityType entityType, Long entityId,
                          Long serviceCategoryId, LocalDate from, LocalDate to, Long organizationId, Long assigneeUserId) {

    public ReportQuery(Set<ReportStatus> statuses, ReportCategory category, ReportEntityType entityType, Long entityId,
                       Long serviceCategoryId, LocalDate from, LocalDate to, Long organizationId) {
        this(statuses, category, entityType, entityId, serviceCategoryId, from, to, organizationId, null);
    }

    public ReportQuery {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }

    public static Set<ReportStatus> statuses(String status) {
        String value = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "OPEN" -> ReportStatus.OPEN;
            case "ALL" -> EnumSet.allOf(ReportStatus.class);
            default -> EnumSet.of(parse(value));
        };
    }

    private static ReportStatus parse(String value) {
        try {
            return ReportStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown report status: " + value);
        }
    }
}
