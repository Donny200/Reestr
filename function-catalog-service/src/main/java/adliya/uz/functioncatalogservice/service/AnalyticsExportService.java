package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import adliya.uz.functioncatalogservice.dto.QualityIssue;
import adliya.uz.functioncatalogservice.dto.QualityQueue;
import adliya.uz.functioncatalogservice.dto.ReportQuery;
import adliya.uz.functioncatalogservice.dto.ReportingPeriod;
import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.service.CsvExport.Delimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsExportService {

    public enum Breakdown { SERVICES, DAILY }

    private static final List<String> EVENT_COLUMNS = Arrays.stream(EngagementEventType.values())
            .map(type -> type.name().toLowerCase(Locale.ROOT)).toList();
    private static final String EDITOR_PATH = "/admin/functions/";

    private final EngagementInsightsService engagement;
    private final ServiceQualityService quality;
    private final ReportReviewService reports;
    private final AnalyticsProperties properties;

    public CsvExport.File engagement(Long organizationId, Long categoryId, LocalDate from, LocalDate to,
                                     Breakdown breakdown, Delimiter delimiter) {
        ReportingPeriod period = engagement.period(from, to);
        String suffix = period.from() + "_" + period.to() + ".csv";
        if (breakdown == Breakdown.DAILY) {
            List<List<Object>> rows = engagement.daily(organizationId, categoryId, period).stream()
                    .map(point -> withCounts(List.of(point.start()), point.counts()))
                    .toList();
            return new CsvExport.File("catalog-engagement-daily_" + suffix,
                    CsvExport.write(delimiter, concat(List.of("date"), EVENT_COLUMNS), rows), false);
        }
        List<List<Object>> rows = engagement.subjects(organizationId, categoryId, period).stream()
                .map(subject -> withCounts(Arrays.asList(period.from(), period.to(), subject.subject(), subject.functionId(),
                        subject.name(), subject.organizationId(), subject.category()), subject.counts()))
                .toList();
        List<String> header = concat(List.of("period_from", "period_to", "subject", "service_id", "service_name",
                "organization_id", "category"), EVENT_COLUMNS);
        return new CsvExport.File("catalog-engagement-services_" + suffix, CsvExport.write(delimiter, header, rows), false);
    }

    public CsvExport.File qualityQueue(Long organizationId, Long categoryId, QualityIssueType issue, FunctionStatus status,
                                       Delimiter delimiter) {
        QualityQueue queue = quality.queue(organizationId, categoryId, issue, status);
        ZoneId zone = properties.getZone();
        List<List<Object>> rows = new ArrayList<>();
        for (QualityQueue.Item item : queue.items()) {
            for (QualityIssue found : item.issues()) {
                if (issue != null && found.type() != issue) {
                    continue;
                }
                rows.add(Arrays.asList(item.functionId(), item.name(), item.status(), item.organizationId(), item.category(),
                        found.type(), found.reason(), String.join(", ", found.details()), item.verificationStatus(),
                        CsvExport.timestamp(item.lastVerifiedAt(), zone), CsvExport.timestamp(item.verificationDueAt(), zone),
                        item.officialSourceUrl(), EDITOR_PATH + item.functionId()));
            }
        }
        List<String> header = List.of("service_id", "service_name", "status", "organization_id", "category", "issue", "reason",
                "details", "verification_status", "last_verified_at (" + zone.getId() + ")",
                "verification_due_at (" + zone.getId() + ")", "official_source_url", "editor_path");
        return new CsvExport.File("quality-queue_" + LocalDate.now(zone) + ".csv", CsvExport.write(delimiter, header, rows), false);
    }

    public CsvExport.File reports(ReportQuery query, Delimiter delimiter) {
        ZoneId zone = properties.getZone();
        List<InformationReport> found = reports.search(query, ReportReviewService.EXPORT_LIMIT + 1);
        boolean truncated = found.size() > ReportReviewService.EXPORT_LIMIT;
        List<List<Object>> rows = found.stream().limit(ReportReviewService.EXPORT_LIMIT)
                .map(report -> Arrays.<Object>asList(report.getId(), CsvExport.timestamp(report.getCreatedAt(), zone),
                        CsvExport.timestamp(report.getUpdatedAt(), zone), report.getStatus(), report.getEntityType(),
                        report.getEntityId(), report.getEntityLabel(), report.getOrganizationId(), report.getCategory(),
                        report.getDescription(), report.getLanguage(), report.getResolutionNote(), report.getHandledByUserId()))
                .toList();
        List<String> header = List.of("report_id", "created_at (" + zone.getId() + ")", "updated_at (" + zone.getId() + ")",
                "status", "entity_type", "entity_id", "entity_label", "organization_id", "category", "description",
                "language", "resolution_note", "handled_by_user_id");
        return new CsvExport.File("visitor-reports_" + LocalDate.now(zone) + ".csv", CsvExport.write(delimiter, header, rows),
                truncated);
    }

    private static List<Object> withCounts(List<Object> leading, Map<EngagementEventType, Long> counts) {
        List<Object> row = new ArrayList<>(leading);
        for (EngagementEventType type : EngagementEventType.values()) {
            row.add(counts.getOrDefault(type, 0L));
        }
        return row;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
