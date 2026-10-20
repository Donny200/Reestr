package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OrganizationSummary(Instant generatedAt, EngagementReport.PeriodInfo period, Long organizationId, Long categoryId,
                                  Map<FunctionStatus, Long> services, long servicesNeedingAttention,
                                  Map<QualityIssueType, Long> attention, boolean translationsChecked,
                                  List<String> activeLanguages, ReportCounts reports, long openReminders,
                                  AssignedToMe assignedToMe) {

    /** Work routed to the calling staff member through their responsibility for cards. */
    public record AssignedToMe(long services, long openReminders, long openReports) {}

    public record ReportCounts(long unresolved, long newReports, long inProgress,
                               long receivedInPeriod, long receivedInPreviousPeriod) {}
}
