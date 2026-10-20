package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import adliya.uz.functioncatalogservice.dto.EngagementReport;
import adliya.uz.functioncatalogservice.dto.EngagementReport.SubjectEngagement;
import adliya.uz.functioncatalogservice.dto.FeedbackReport;
import adliya.uz.functioncatalogservice.dto.OrganizationSummary;
import adliya.uz.functioncatalogservice.dto.QualityIssue;
import adliya.uz.functioncatalogservice.dto.QualityQueue;
import adliya.uz.functioncatalogservice.dto.ReportQuery;
import adliya.uz.functioncatalogservice.dto.ReportingPeriod;
import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.entity.ReportStatus;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.repository.FunctionCategoryRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import adliya.uz.functioncatalogservice.service.InterfaceLabels.Labels;
import adliya.uz.functioncatalogservice.service.ReportDocument.Column;
import adliya.uz.functioncatalogservice.service.ReportDocument.Fact;
import adliya.uz.functioncatalogservice.service.ReportDocument.Section;
import adliya.uz.functioncatalogservice.service.ReportDocument.Table;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Excel and PDF versions of the organization dashboard and of the visitor-report list. Every figure comes from the
 * same scoped services that feed the screens, so an export can never contain more than its caller may see: staff get
 * their own organizations only and a foreign organization id is refused before anything is read. Visitor contact
 * details, report texts, staff notes and staff identifiers are never written.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationReportService {

    public enum Format { XLSX, PDF }

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM");
    /** English wording of the SPA, used when reference-service has no label for the report language. */
    private static final Map<String, String> FALLBACKS = Map.ofEntries(
            Map.entry("analytics.event.CATALOG_VIEW", "Catalog page views"),
            Map.entry("analytics.event.SERVICE_VIEW", "Service page views"),
            Map.entry("analytics.event.OFFICIAL_LINK_CLICK", "Official website clicks"),
            Map.entry("analytics.event.PHONE_CLICK", "Phone link clicks"),
            Map.entry("analytics.event.MAP_CLICK", "Map link clicks"),
            Map.entry("analytics.event.PRINT", "Prints"),
            Map.entry("fnAdmin.status.DRAFT", "Draft"),
            Map.entry("fnAdmin.status.PENDING_REVIEW", "Pending review"),
            Map.entry("fnAdmin.status.PUBLISHED", "Published"),
            Map.entry("fnAdmin.status.DEACTIVATED", "Deactivated"),
            Map.entry("quality.issue.VERIFICATION_OVERDUE", "Verification overdue"),
            Map.entry("quality.issue.SOURCE_MISSING", "External source link unusable"),
            Map.entry("quality.issue.INFORMATION_INCOMPLETE", "Required information incomplete"),
            Map.entry("quality.issue.TRANSLATIONS_MISSING", "Translations missing"),
            Map.entry("quality.reason.NEVER_VERIFIED", "Published but never marked as verified by the organization. Review the card and mark it as verified."),
            Map.entry("quality.reason.RECHECK_DUE", "The last review is more than 180 days old. Review the card again."),
            Map.entry("quality.reason.CHANGED_SINCE_VERIFICATION", "The card changed after its last review. Review it again."),
            Map.entry("quality.reason.UNUSABLE", "The external source link is not a valid web address. Replace it with a full https:// link or remove it."),
            Map.entry("quality.reason.REQUIRED_FIELDS", "Fill in the required fields:"),
            Map.entry("quality.reason.LANGUAGES", "Add translations for:"),
            Map.entry("verification.status.UNVERIFIED", "Not verified"),
            Map.entry("verification.status.VERIFIED", "Verified by organization"),
            Map.entry("verification.status.DUE", "Recheck due"),
            Map.entry("verification.status.OUTDATED", "Changed since verification"),
            Map.entry("reports.status.NEW", "New"),
            Map.entry("reports.status.IN_PROGRESS", "In progress"),
            Map.entry("reports.status.RESOLVED", "Resolved"),
            Map.entry("reports.status.REJECTED", "Rejected"),
            Map.entry("report.category.INCORRECT_INFORMATION", "Information is incorrect"),
            Map.entry("report.category.OUTDATED_INFORMATION", "Information is outdated"),
            Map.entry("report.category.DOCUMENTS_OR_STEPS", "Documents or steps are wrong"),
            Map.entry("report.category.FEES_OR_TIMING", "Fee or processing time is wrong"),
            Map.entry("report.category.CONTACT_DETAILS", "Address, phone or hours are wrong"),
            Map.entry("report.category.BROKEN_LINK", "A link does not work"),
            Map.entry("report.category.TRANSLATION", "Translation problem"),
            Map.entry("report.category.OTHER", "Something else"));
    private static final List<EngagementEventType> EVENTS = Arrays.asList(EngagementEventType.values());

    private final OrganizationSummaryService summaries;
    private final EngagementInsightsService engagement;
    private final ServiceQualityService quality;
    private final FeedbackService feedback;
    private final ReportReviewService reports;
    private final FunctionCategoryRepository categories;
    private final IdentityOrganizationClient organizations;
    private final InterfaceLabels interfaceLabels;
    private final CatalogAccess access;
    private final AnalyticsProperties properties;

    public FileDownload organizationReport(Long organizationId, Long categoryId, LocalDate from, LocalDate to,
                                           String language, Format format) {
        access.scope(organizationId);
        Labels labels = interfaceLabels.forLanguage(language);
        Names names = names(labels);
        ReportingPeriod period = engagement.period(from, to);
        OrganizationSummary summary = summaries.summary(organizationId, categoryId, period.from(), period.to());
        EngagementInsightsService.Activity engagementActivity = engagement.activity(organizationId, categoryId, period.from(),
                period.to(), EngagementReport.Granularity.AUTO);
        EngagementReport activity = engagementActivity.report();
        List<SubjectEngagement> subjects = engagementActivity.subjects();
        FeedbackReport votes = feedback.report(organizationId, categoryId, period.from(), period.to());
        QualityQueue queue = quality.queue(organizationId, categoryId, null, null);
        boolean includeReports = access.canViewReports();
        List<InformationReport> received = includeReports
                ? reports.search(new ReportQuery(EnumSet.allOf(ReportStatus.class), null, null, null, categoryId,
                period.from(), period.to(), organizationId), ReportReviewService.EXPORT_LIMIT + 1)
                : List.of();
        boolean truncated = received.size() > ReportReviewService.EXPORT_LIMIT;

        List<Fact> facts = new ArrayList<>(filterFacts(labels, names, organizationId, categoryId));
        facts.add(new Fact(labels.get("export.filter.period", "Period"), range(period.from(), period.to())));
        facts.add(new Fact(labels.get("export.filter.previousPeriod", "Previous period"),
                range(period.previous().from(), period.previous().to())));
        facts.addAll(commonFacts(labels));

        List<String> notes = new ArrayList<>();
        notes.add(labels.get("analytics.filterScope", "Status counts, quality issues and unresolved reports show the current "
                + "state. The period applies to catalog engagement and to reports received."));
        notes.add(labels.get("analytics.disclaimer", "Engagement figures count anonymous views and link clicks on this "
                + "information site. They are not applications, customers or completed services, and staff visits are not counted."));
        notes.add(privacyNote(labels));
        if (!includeReports) {
            notes.add(labels.get("export.reportsOmitted", "Visitor reports are not included because your role cannot view them."));
        }

        List<Section> sections = new ArrayList<>();
        sections.add(summarySection(labels, summary, activity, votes));
        sections.add(activitySection(labels, names, subjects, votes));
        sections.add(trendSection(labels, activity));
        sections.add(qualitySection(labels, names, queue));
        if (includeReports) {
            sections.add(reportsSection(labels, names, received, truncated));
        }
        ReportDocument document = document(labels.get("export.report.title", "Organization report"), facts, notes, sections, labels);
        String name = "organization-report_" + period.from() + "_" + period.to();
        return render(document, name, format, truncated);
    }

    /** Visitor-report list with the filters of the reports screen; descriptions and contacts are left out. */
    public FileDownload visitorReports(ReportQuery query, String language, Format format) {
        access.scope(query.organizationId());
        Labels labels = interfaceLabels.forLanguage(language);
        Names names = names(labels);
        List<InformationReport> found = reports.search(query, ReportReviewService.EXPORT_LIMIT + 1);
        boolean truncated = found.size() > ReportReviewService.EXPORT_LIMIT;

        List<Fact> facts = new ArrayList<>(filterFacts(labels, names, query.organizationId(), query.serviceCategoryId()));
        facts.add(new Fact(labels.get("export.filter.status", "Status"), query.statuses().equals(ReportStatus.OPEN)
                ? labels.get("reports.filter.OPEN", "Open")
                : query.statuses().equals(EnumSet.allOf(ReportStatus.class)) ? labels.get("reports.filter.ALL", "All")
                : query.statuses().stream().map(status -> reportStatus(labels, status)).collect(Collectors.joining(", "))));
        if (query.category() != null) {
            facts.add(new Fact(labels.get("reports.category", "Problem"), reportCategory(labels, query.category().name())));
        }
        if (query.entityType() != null) {
            facts.add(new Fact(labels.get("export.col.subjectType", "Subject"), subjectType(labels, query.entityType().name())
                    + (query.entityId() == null ? "" : " #" + query.entityId())));
        }
        if (query.from() != null || query.to() != null) {
            facts.add(new Fact(labels.get("export.filter.received", "Received"), range(query.from(), query.to())));
        }
        facts.addAll(commonFacts(labels));
        List<String> notes = List.of(privacyNote(labels));
        Section section = reportsSection(labels, names, found, truncated);
        Section first = new Section(labels.get("export.sheet.reports", "Visitor reports"),
                labels.get("export.sheet.reports", "Visitor reports"), section.note(), section.tables(), null);
        ReportDocument document = document(labels.get("export.reports.title", "Visitor reports"), facts, notes, List.of(first), labels);
        return render(document, "visitor-reports_" + LocalDate.now(properties.getZone()), format, truncated);
    }

    private Section summarySection(Labels labels, OrganizationSummary summary, EngagementReport activity, FeedbackReport votes) {
        List<Column> comparisonColumns = List.of(Column.text(labels.get("analytics.metric", "Metric"), 3f),
                Column.integer(labels.get("export.col.selectedPeriod", "Selected period")),
                Column.integer(labels.get("export.col.previousPeriod", "Previous period")),
                Column.percent(labels.get("export.col.change", "Change")));
        List<List<Object>> comparison = new ArrayList<>();
        comparison.add(compare(labels.get("export.metric.reportsReceived", "Visitor reports received"),
                summary.reports().receivedInPeriod(), summary.reports().receivedInPreviousPeriod()));
        for (EngagementEventType type : EVENTS) {
            comparison.add(compare(event(labels, type), activity.totals().getOrDefault(type, 0L),
                    activity.previousTotals().getOrDefault(type, 0L)));
        }
        comparison.add(compare(labels.get("export.metric.helpful", "“Helpful” votes"),
                votes.totals().helpful(), votes.previousTotals().helpful()));
        comparison.add(compare(labels.get("export.metric.notHelpful", "“Not helpful” votes"),
                votes.totals().notHelpful(), votes.previousTotals().notHelpful()));

        List<Column> stateColumns = List.of(Column.text(labels.get("analytics.metric", "Metric"), 3f),
                Column.integer(labels.get("export.col.value", "Value")));
        List<List<Object>> state = new ArrayList<>();
        for (FunctionStatus status : List.of(FunctionStatus.PUBLISHED, FunctionStatus.PENDING_REVIEW, FunctionStatus.DRAFT,
                FunctionStatus.DEACTIVATED)) {
            state.add(row(labels.get("export.metric.cards", "Service cards") + ": " + functionStatus(labels, status),
                    summary.services().getOrDefault(status, 0L)));
        }
        state.add(row(stripColon(labels.get("analytics.cardsWithIssues", "Cards with at least one issue")),
                summary.servicesNeedingAttention()));
        for (QualityIssueType type : QualityIssueType.values()) {
            boolean unknown = type == QualityIssueType.TRANSLATIONS_MISSING && !summary.translationsChecked();
            state.add(row(qualityIssue(labels, type), unknown ? null : summary.attention().getOrDefault(type, 0L)));
        }
        state.add(row(labels.get("export.metric.unresolvedReports", "Unresolved visitor reports"), summary.reports().unresolved()));
        state.add(row(labels.get("export.metric.newReports", "Visitor reports: new"), summary.reports().newReports()));
        state.add(row(labels.get("export.metric.inProgressReports", "Visitor reports: in progress"), summary.reports().inProgress()));
        state.add(row(labels.get("export.metric.openReminders", "Open quality reminders"), summary.openReminders()));

        return new Section(labels.get("export.sheet.summary", "Summary"), labels.get("export.sheet.summary", "Summary"), null,
                List.of(new Table(labels.get("export.periodComparison", "Selected period compared with the previous period"),
                                comparisonColumns, comparison),
                        new Table(labels.get("analytics.snapshotTitle", "Service cards now"), stateColumns, state)), null);
    }

    private Section activitySection(Labels labels, Names names, List<SubjectEngagement> subjects, FeedbackReport votes) {
        List<Column> columns = new ArrayList<>(List.of(Column.integer(labels.get("export.col.serviceId", "Service ID")),
                Column.text(labels.get("export.col.service", "Service"), 3.2f),
                Column.text(labels.get("export.col.status", "Status"), 1.3f),
                Column.text(labels.get("field.organization", "Organization"), 2f),
                Column.text(labels.get("field.category", "Category"), 1.4f)));
        EVENTS.forEach(type -> columns.add(Column.integer(event(labels, type))));
        columns.add(Column.integer(labels.get("export.metric.helpful", "“Helpful” votes")));
        columns.add(Column.integer(labels.get("export.metric.notHelpful", "“Not helpful” votes")));

        Map<Long, FeedbackReport.ServiceFeedback> feedbackByService = votes.services().stream()
                .filter(item -> item.functionId() != null)
                .collect(Collectors.toMap(FeedbackReport.ServiceFeedback::functionId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<List<Object>> rows = new ArrayList<>();
        for (SubjectEngagement subject : subjects) {
            List<Object> row = new ArrayList<>();
            if (subject.subject() == EngagementReport.SubjectType.SERVICE) {
                FeedbackReport.ServiceFeedback found = feedbackByService.remove(subject.functionId());
                row.addAll(Arrays.asList(subject.functionId(), LocalizedContent.text(subject.name(), subject.nameTranslations(),
                                labels.language()), functionStatus(labels, subject.status()),
                        names.organization(subject.organizationId()), names.category(subject.categoryId(), subject.category())));
                EVENTS.forEach(type -> row.add(subject.counts().getOrDefault(type, 0L)));
                row.add(found == null ? 0L : found.helpful());
                row.add(found == null ? 0L : found.notHelpful());
            } else {
                String label = subject.subject() == EngagementReport.SubjectType.ORGANIZATION
                        ? labels.get("export.subject.ORGANIZATION", "Organization page")
                        : labels.get("export.subject.CATALOG", "Catalog pages (all organizations)");
                row.addAll(Arrays.asList(null, label, null, names.organization(subject.organizationId()), null));
                EVENTS.forEach(type -> row.add(subject.counts().getOrDefault(type, 0L)));
                row.add(null);
                row.add(null);
            }
            rows.add(row);
        }
        for (FeedbackReport.ServiceFeedback item : feedbackByService.values()) {
            List<Object> row = new ArrayList<>(Arrays.asList(item.functionId(),
                    LocalizedContent.text(item.name(), item.nameTranslations(), labels.language()), functionStatus(labels, item.status()),
                    names.organization(item.organizationId()), names.category(item.categoryId(), item.category())));
            EVENTS.forEach(type -> row.add(0L));
            row.add(item.helpful());
            row.add(item.notHelpful());
            rows.add(row);
        }
        return new Section(labels.get("export.sheet.activity", "Service activity"), labels.get("export.sheet.activity", "Service activity"),
                labels.get("export.activityNote", "Totals for the selected period per service page, organization page and catalog. "
                        + "Counts are anonymous views, link clicks and prints, not applications."),
                List.of(new Table(null, columns, rows)), null);
    }

    private Section trendSection(Labels labels, EngagementReport activity) {
        boolean daily = activity.granularity() == EngagementReport.Granularity.DAY;
        List<Column> columns = new ArrayList<>(List.of(Column.date(labels.get("export.col.start", "From")),
                Column.date(labels.get("export.col.end", "To"))));
        EVENTS.forEach(type -> columns.add(Column.integer(event(labels, type))));
        List<List<Object>> rows = new ArrayList<>();
        List<String> chartLabels = new ArrayList<>();
        List<Long> chartValues = new ArrayList<>();
        for (EngagementReport.SeriesPoint point : activity.series()) {
            List<Object> row = new ArrayList<>(List.of(point.start(), point.end()));
            EVENTS.forEach(type -> row.add(point.counts().getOrDefault(type, 0L)));
            rows.add(row);
            chartLabels.add(DAY_MONTH.format(point.start()));
            chartValues.add(point.counts().getOrDefault(EngagementEventType.SERVICE_VIEW, 0L));
        }
        String trend = daily ? labels.get("analytics.dailyTrend", "Daily trend") : labels.get("analytics.weeklyTrend", "Weekly trend");
        boolean any = activity.series().stream().anyMatch(point -> point.counts().values().stream().anyMatch(value -> value > 0));
        return new Section(labels.get("export.sheet.trend", "Activity over time"), labels.get("export.sheet.trend", "Activity over time"),
                trend + " (" + labels.get("analytics.timeZone", "Tashkent time") + ")",
                List.of(new Table(null, columns, any ? rows : List.of())),
                new ReportDocument.Chart(event(labels, EngagementEventType.SERVICE_VIEW) + " · " + trend, chartLabels, chartValues));
    }

    private Section qualitySection(Labels labels, Names names, QualityQueue queue) {
        List<Column> columns = List.of(Column.integer(labels.get("export.col.serviceId", "Service ID")),
                Column.text(labels.get("export.col.service", "Service"), 3f),
                Column.text(labels.get("export.col.status", "Status"), 1.3f),
                Column.text(labels.get("field.organization", "Organization"), 1.8f),
                Column.text(labels.get("field.category", "Category"), 1.3f),
                Column.text(labels.get("export.col.issue", "Issue"), 1.8f),
                Column.text(labels.get("export.col.details", "What to do"), 3.4f),
                Column.text(labels.get("verification.column", "Verification"), 1.4f),
                Column.dateTime(labels.get("verification.lastVerified", "Last verified")),
                Column.dateTime(labels.get("export.col.verificationDue", "Review due")));
        List<List<Object>> rows = new ArrayList<>();
        for (QualityQueue.Item item : queue.items()) {
            for (QualityIssue issue : item.issues()) {
                rows.add(Arrays.asList(item.functionId(), LocalizedContent.text(item.name(), item.nameTranslations(), labels.language()),
                        functionStatus(labels, item.status()), names.organization(item.organizationId()),
                        names.category(item.categoryId(), item.category()), qualityIssue(labels, issue.type()),
                        guidance(labels, issue), label(labels, "verification.status." + item.verificationStatus(),
                                String.valueOf(item.verificationStatus())),
                        item.lastVerifiedAt(), item.verificationDueAt()));
            }
        }
        String note = queue.translationsChecked() ? null
                : labels.get("quality.languagesUnknown", "Language list unavailable");
        return new Section(labels.get("export.sheet.quality", "Quality issues"), labels.get("export.sheet.quality", "Quality issues"),
                note, List.of(new Table(null, columns, rows)), null);
    }

    private Section reportsSection(Labels labels, Names names, List<InformationReport> found, boolean truncated) {
        List<Column> columns = List.of(Column.integer(labels.get("export.col.reportId", "Report ID")),
                Column.dateTime(labels.get("export.col.received", "Received")),
                Column.dateTime(labels.get("export.col.updated", "Last updated")),
                Column.text(labels.get("export.col.status", "Status"), 1.3f),
                Column.text(labels.get("reports.category", "Problem"), 2.2f),
                Column.text(labels.get("export.col.subjectType", "Subject"), 1.2f),
                Column.text(labels.get("export.col.linkedSubject", "Linked service or organization"), 3f),
                Column.integer(labels.get("export.col.subjectId", "Subject ID")),
                Column.text(labels.get("field.organization", "Organization"), 2f));
        List<List<Object>> rows = found.stream().limit(ReportReviewService.EXPORT_LIMIT)
                .map(report -> Arrays.<Object>asList(report.getId(), report.getCreatedAt(), report.getUpdatedAt(),
                        reportStatus(labels, report.getStatus()), reportCategory(labels, report.getCategory().name()),
                        subjectType(labels, report.getEntityType().name()), report.getEntityLabel(), report.getEntityId(),
                        names.organization(report.getOrganizationId())))
                .toList();
        String note = truncated
                ? labels.get("export.truncated", "The export contains the first 10,000 rows. Narrow the filters to export the rest.")
                : null;
        return new Section(labels.get("export.sheet.reports", "Visitor reports"), labels.get("export.sheet.reports", "Visitor reports"),
                note, List.of(new Table(null, columns, rows)), null);
    }

    private List<Fact> filterFacts(Labels labels, Names names, Long organizationId, Long categoryId) {
        String organization;
        if (organizationId != null) {
            organization = names.organization(organizationId);
        } else if (access.global()) {
            organization = labels.get("analytics.allOrganizations", "All organizations");
        } else {
            OrganizationScope scope = access.scope(null);
            organization = labels.get("analytics.allMyOrganizations", "All my organizations") + ": "
                    + scope.organizationIds().stream().sorted().map(names::organization).collect(Collectors.joining(", "));
        }
        String category = categoryId == null ? labels.get("analytics.allCategories", "All categories") : names.category(categoryId, null);
        return List.of(new Fact(labels.get("field.organization", "Organization"), organization),
                new Fact(labels.get("analytics.category", "Service category"), category));
    }

    private List<Fact> commonFacts(Labels labels) {
        Instant now = Instant.now();
        return List.of(new Fact(labels.get("export.generatedAt", "Generated"),
                        CsvExport.timestamp(now, properties.getZone()) + " (" + properties.getZone().getId() + ")"),
                new Fact(labels.get("export.language", "Language"), labels.language()));
    }

    private ReportDocument document(String title, List<Fact> facts, List<String> notes, List<Section> sections, Labels labels) {
        return new ReportDocument(title, Instant.now(), properties.getZone(), labels.locale(), facts, notes, sections,
                new ReportDocument.Labels(labels.get("export.empty", "No data for the selected filters."),
                        labels.get("export.generatedAt", "Generated"), labels.get("export.page", "Page"),
                        labels.get("export.pdfRowLimit", "Only the first {count} rows are shown. Download the Excel file for the full list.")));
    }

    private static FileDownload render(ReportDocument document, String name, Format format, boolean truncated) {
        return format == Format.PDF
                ? new FileDownload(name + ".pdf", FileDownload.PDF, PdfReportWriter.write(document), truncated)
                : new FileDownload(name + ".xlsx", FileDownload.XLSX, XlsxReportWriter.write(document), truncated);
    }

    private Names names(Labels labels) {
        Map<Long, IdentityOrganizationClient.PublicOrganization> directory = organizations.publicDirectory();
        Map<Long, FunctionCategory> byId = categories.findAll().stream()
                .collect(Collectors.toMap(FunctionCategory::getId, Function.identity()));
        return new Names(labels.language(), directory, byId);
    }

    private static String privacyNote(Labels labels) {
        return labels.get("export.privacyNote", "Visitor contact details, report texts, staff notes and staff identities are not included.");
    }

    private static List<Object> compare(String label, long current, long previous) {
        Double change = previous == 0 ? null : (current - previous) / (double) previous;
        return Arrays.asList(label, current, previous, change);
    }

    private static List<Object> row(String label, Long value) {
        return Arrays.asList(label, value);
    }

    private static String range(LocalDate from, LocalDate to) {
        return (from == null ? "…" : from.toString()) + " – " + (to == null ? "…" : to.toString());
    }

    private static String stripColon(String text) {
        return text.endsWith(":") ? text.substring(0, text.length() - 1).strip() : text;
    }

    private static String label(Labels labels, String key, String raw) {
        return labels.get(key, FALLBACKS.getOrDefault(key, raw));
    }

    private static String event(Labels labels, EngagementEventType type) {
        return label(labels, "analytics.event." + type, type.name());
    }

    private static String functionStatus(Labels labels, FunctionStatus status) {
        return status == null ? null : label(labels, "fnAdmin.status." + status, status.name());
    }

    private static String qualityIssue(Labels labels, QualityIssueType type) {
        return label(labels, "quality.issue." + type, type.name());
    }

    private static String reportStatus(Labels labels, ReportStatus status) {
        return label(labels, "reports.status." + status, status.name());
    }

    private static String reportCategory(Labels labels, String category) {
        return label(labels, "report.category." + category, category);
    }

    private static String subjectType(Labels labels, String type) {
        return "FUNCTION".equals(type) ? labels.get("reports.typeFunction", "Service")
                : labels.get("reports.typeOrganization", "Organization");
    }

    private static String guidance(Labels labels, QualityIssue issue) {
        String base = label(labels, "quality.reason." + issue.reason(), issue.reason());
        if (issue.details().isEmpty()) {
            return base;
        }
        List<String> details = issue.details().stream().map(detail -> switch (detail) {
            case "name" -> labels.get("field.name", "Name");
            case "description" -> labels.get("field.description", "Description");
            case "organization" -> labels.get("field.organization", "Organization");
            default -> QualityIssue.LANGUAGES.equals(issue.reason()) ? detail.toUpperCase(Locale.ROOT) : detail;
        }).toList();
        return QualityIssue.UNUSABLE.equals(issue.reason()) ? base + " (" + details.get(0) + ")" : base + " " + String.join(", ", details);
    }

    /** Localized organization and category names; unknown ids are shown as "#id" rather than guessed. */
    private record Names(String language, Map<Long, IdentityOrganizationClient.PublicOrganization> organizations,
                         Map<Long, FunctionCategory> categories) {
        String organization(Long id) {
            if (id == null) {
                return null;
            }
            IdentityOrganizationClient.PublicOrganization found = organizations.get(id);
            String name = found == null ? null : LocalizedContent.text(found.name(), found.nameTranslations(), language);
            return name == null || name.isBlank() ? "#" + id : name;
        }

        String category(Long id, String fallback) {
            FunctionCategory found = id == null ? null : categories.get(id);
            if (found == null) {
                return fallback != null ? fallback : id == null ? null : "#" + id;
            }
            Map<String, TranslatedText> translations = found.getNameTranslations();
            return Objects.requireNonNullElse(LocalizedContent.text(found.getName(), translations, language), found.getName());
        }
    }
}
