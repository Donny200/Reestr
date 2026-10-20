package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.EngagementReport;
import adliya.uz.functioncatalogservice.dto.FeedbackReport;
import adliya.uz.functioncatalogservice.service.FeedbackService;
import adliya.uz.functioncatalogservice.dto.OrganizationSummary;
import adliya.uz.functioncatalogservice.dto.QualityQueue;
import adliya.uz.functioncatalogservice.dto.QualityReminderResponse;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.service.AnalyticsExportService;
import adliya.uz.functioncatalogservice.service.CsvExport;
import adliya.uz.functioncatalogservice.service.EngagementInsightsService;
import adliya.uz.functioncatalogservice.service.OrganizationReportService;
import adliya.uz.functioncatalogservice.service.OrganizationSummaryService;
import adliya.uz.functioncatalogservice.service.QualityReminderService;
import adliya.uz.functioncatalogservice.service.ServiceQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORG_ANALYTICS_VIEW')")
@Tag(name = "Organization analytics", description = "Organization dashboard data scoped to the caller's organizations. "
        + "Engagement figures describe interest in catalog information only; they are not applications or completed services.")
public class OrganizationInsightsController {

    private final EngagementInsightsService engagement;
    private final OrganizationSummaryService summaries;
    private final ServiceQualityService quality;
    private final QualityReminderService reminders;
    private final AnalyticsExportService exports;
    private final FeedbackService feedback;
    private final OrganizationReportService reports;

    @GetMapping("/summary")
    @Operation(summary = "Dashboard summary",
            description = "Current service status counts and quality issues (a snapshot, not filtered by period), "
                    + "plus visitor report counts for the selected period and the preceding period of equal length.")
    public OrganizationSummary summary(@RequestParam(required = false) Long organizationId,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return summaries.summary(organizationId, categoryId, from, to);
    }

    @GetMapping("/engagement")
    @Operation(summary = "Catalog engagement for a period",
            description = "Daily aggregates of public catalog views and link clicks in the Asia/Tashkent calendar, "
                    + "compared with the preceding period of equal length.")
    public EngagementReport engagement(@RequestParam(required = false) Long organizationId,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) EngagementReport.Granularity granularity) {
        return engagement.report(organizationId, categoryId, from, to, granularity);
    }

    @GetMapping("/engagement/export")
    @Operation(summary = "Export catalog engagement as CSV",
            description = "UTF-8 CSV with a byte order mark. SERVICES lists totals per service, organization page and catalog; "
                    + "DAILY lists totals per calendar day. Counts are views and link clicks, not applications.")
    public ResponseEntity<byte[]> exportEngagement(@RequestParam(required = false) Long organizationId,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "SERVICES") AnalyticsExportService.Breakdown breakdown,
                                                   @RequestParam(defaultValue = "COMMA") CsvExport.Delimiter delimiter) {
        return CsvExport.attachment(exports.engagement(organizationId, categoryId, from, to, breakdown, delimiter));
    }

    @GetMapping("/report/export")
    @Operation(summary = "Export the organization report as Excel or PDF",
            description = "Same filters and scope as the dashboard. XLSX has the sheets Summary, Service activity, Activity over "
                    + "time, Quality issues and (with REPORTS_VIEW or ORG_REPORTS_MANAGE) Visitor reports; PDF is a printable "
                    + "version of the same content. Labels follow lang (an interface language code). Visitor contact details, "
                    + "report texts and staff identities are never included.")
    public ResponseEntity<byte[]> exportReport(@RequestParam(required = false) Long organizationId,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "XLSX") OrganizationReportService.Format format,
                                               @RequestParam(required = false) String lang) {
        return reports.organizationReport(organizationId, categoryId, from, to, lang, format).attachment();
    }

    @GetMapping("/quality-queue")
    @Operation(summary = "Service cards that need attention",
            description = "Cards with overdue verification, a missing or unusable official source, missing required fields, "
                    + "or missing translations for the active interface languages. Deactivated cards are excluded.")
    public QualityQueue qualityQueue(@RequestParam(required = false) Long organizationId,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) QualityIssueType issue,
                                     @RequestParam(required = false) FunctionStatus status) {
        return quality.queue(organizationId, categoryId, issue, status);
    }

    @GetMapping("/quality-queue/export")
    @Operation(summary = "Export the quality queue as CSV", description = "One row per card and issue, with the editor path.")
    public ResponseEntity<byte[]> exportQualityQueue(@RequestParam(required = false) Long organizationId,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) QualityIssueType issue,
                                                     @RequestParam(required = false) FunctionStatus status,
                                                     @RequestParam(defaultValue = "COMMA") CsvExport.Delimiter delimiter) {
        return CsvExport.attachment(exports.qualityQueue(organizationId, categoryId, issue, status, delimiter));
    }

    @GetMapping("/reminders")
    @Operation(summary = "Open quality reminders",
            description = "Reminders created by the daily quality scan that nobody has acknowledged yet, newest first. "
                    + "Lists at most 200; total counts all of them.")
    public QualityReminderResponse.Page reminders(@RequestParam(required = false) Long organizationId,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) String assignee) {
        return reminders.active(organizationId, categoryId, AssigneeParameter.me(assignee));
    }

    @GetMapping("/feedback")
    @Operation(summary = "Was this information helpful? aggregates",
            description = "Anonymous helpful / not helpful counts per service for the period, compared with the preceding period. "
                    + "They describe the usefulness of the published text, not the organization, the government service or its staff.")
    public FeedbackReport feedback(@RequestParam(required = false) Long organizationId,
                                   @RequestParam(required = false) Long categoryId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return feedback.report(organizationId, categoryId, from, to);
    }

    @PostMapping("/reminders/{id}/acknowledge")
    @Operation(summary = "Acknowledge a reminder",
            description = "Hides the reminder for the organization; it returns after the renotify interval if the issue persists.")
    public ResponseEntity<Void> acknowledge(@PathVariable Long id) {
        reminders.acknowledge(id);
        return ResponseEntity.noContent().build();
    }
}
