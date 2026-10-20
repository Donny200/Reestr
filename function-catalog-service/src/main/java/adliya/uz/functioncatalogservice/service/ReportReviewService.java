package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import adliya.uz.functioncatalogservice.dto.OrganizationSummary;
import adliya.uz.functioncatalogservice.dto.ReportQuery;
import adliya.uz.functioncatalogservice.dto.ReportingPeriod;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.AuditLog;
import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.entity.ReportStatus;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.AuditLogRepository;
import adliya.uz.functioncatalogservice.repository.InformationReportRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportReviewService {

    public static final int LIST_LIMIT = 500;
    public static final int EXPORT_LIMIT = 10_000;
    private static final Set<AuditAction> CARD_CHANGES = EnumSet.of(
            AuditAction.UPDATE, AuditAction.TRANSLATION_EDIT, AuditAction.CATEGORY_CHANGE);

    private final InformationReportRepository reports;
    private final AuditLogRepository auditLogs;
    private final CatalogAccess access;
    private final AuditWriter audit;
    private final AnalyticsProperties analytics;

    public List<InformationReport> list(ReportQuery query) {
        return search(query, LIST_LIMIT);
    }

    public List<InformationReport> search(ReportQuery query, int limit) {
        OrganizationScope scope = access.scope(query.organizationId());
        if (scope.empty()) {
            return List.of();
        }
        return reports.findAll(specification(query, scope), PageRequest.of(0, limit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).getContent();
    }

    /** Number of reports {@link #search} would find without a limit. */
    public long count(ReportQuery query) {
        OrganizationScope scope = access.scope(query.organizationId());
        return scope.empty() ? 0 : reports.count(specification(query, scope));
    }

    public OrganizationSummary.ReportCounts counts(OrganizationScope scope, Long serviceCategoryId, ReportingPeriod period) {
        if (scope.empty()) {
            return new OrganizationSummary.ReportCounts(0, 0, 0, 0, 0);
        }
        Specification<InformationReport> scoped = ScopeSpecifications.inScope(scope);
        Specification<InformationReport> base = serviceCategoryId == null ? scoped : scoped.and(inServiceCategory(serviceCategoryId));
        ReportingPeriod previous = period.previous();
        return new OrganizationSummary.ReportCounts(
                reports.count(base.and(withStatus(ReportStatus.OPEN))),
                reports.count(base.and(withStatus(Set.of(ReportStatus.NEW)))),
                reports.count(base.and(withStatus(Set.of(ReportStatus.IN_PROGRESS)))),
                reports.count(base.and(createdWithin(period))),
                reports.count(base.and(createdWithin(previous))));
    }

    public InformationReport get(Long id) {
        InformationReport report = reports.findById(id).orElseThrow(() -> new NoSuchElementException("Report not found: " + id));
        access.requireOrganization(report.getOrganizationId());
        return report;
    }

    public Boolean serviceChangedSince(InformationReport report) {
        return report.getEntityType() == ReportEntityType.FUNCTION
                ? auditLogs.existsFunctionChange(report.getEntityId(), CARD_CHANGES, report.getCreatedAt())
                : null;
    }

    public List<AuditLog> history(Long id) {
        get(id);
        return auditLogs.findAllByEntityTypeAndEntityIdOrderByPerformedAtAscIdAsc(AuditWriter.REPORT_ENTITY, id);
    }

    public boolean revealsContact() {
        return access.canManageReports();
    }

    @Transactional
    public InformationReport changeStatus(Long id, ReportStatus target, String note) {
        access.requireReportManager();
        InformationReport report = get(id);
        ReportStatus previous = report.getStatus();
        if (!previous.canMoveTo(target)) {
            throw new WorkflowConflictException("Report cannot move from " + previous + " to " + target
                    + "; allowed: " + allowedTargets(previous));
        }
        String explanation = note == null || note.isBlank() ? null : note.strip();
        if (target == ReportStatus.REJECTED && explanation == null) {
            throw new InvalidFieldException("note", "An explanation is required to reject a report");
        }
        AuditWriter.Actor actor = audit.actor();
        report.setStatus(target);
        report.setHandledByUserId(actor.id());
        report.setUpdatedAt(Instant.now());
        if (explanation != null) {
            report.setResolutionNote(explanation);
        }
        if (target.closed()) {
            report.setContact(null);
        }
        reports.saveAndFlush(report);
        audit.reportStatusChanged(report, previous, explanation);
        return report;
    }

    @Transactional
    public void reassignFunctionReports(Long functionId, Long organizationId) {
        reports.reassignOrganization(ReportEntityType.FUNCTION, functionId, organizationId);
    }

    private Specification<InformationReport> specification(ReportQuery query, OrganizationScope scope) {
        Specification<InformationReport> specification = ScopeSpecifications.<InformationReport>inScope(scope).and(withStatus(query.statuses()));
        if (query.category() != null) {
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("category"), query.category()));
        }
        if (query.entityType() != null) {
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("entityType"), query.entityType()));
        }
        if (query.entityId() != null) {
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("entityId"), query.entityId()));
        }
        if (query.serviceCategoryId() != null) {
            specification = specification.and(inServiceCategory(query.serviceCategoryId()));
        }
        if (query.assigneeUserId() != null) {
            specification = specification.and(assignedTo(query.assigneeUserId()));
        }
        if (query.from() != null) {
            Instant start = query.from().atStartOfDay(analytics.getZone()).toInstant();
            specification = specification.and((root, criteria, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (query.to() != null) {
            Instant end = query.to().plusDays(1).atStartOfDay(analytics.getZone()).toInstant();
            specification = specification.and((root, criteria, builder) -> builder.lessThan(root.get("createdAt"), end));
        }
        return specification;
    }

    private Specification<InformationReport> createdWithin(ReportingPeriod period) {
        Instant start = period.start(analytics.getZone());
        Instant end = period.endExclusive(analytics.getZone());
        return (root, criteria, builder) -> builder.and(
                builder.greaterThanOrEqualTo(root.get("createdAt"), start), builder.lessThan(root.get("createdAt"), end));
    }

    private static Specification<InformationReport> withStatus(Set<ReportStatus> statuses) {
        return (root, criteria, builder) -> root.get("status").in(statuses);
    }

    private static Specification<InformationReport> inServiceCategory(Long categoryId) {
        return (root, criteria, builder) -> {
            Subquery<Long> cards = criteria.subquery(Long.class);
            Root<OrgFunction> card = cards.from(OrgFunction.class);
            cards.select(card.get("id")).where(builder.equal(card.get("functionCategory").get("id"), categoryId));
            return builder.and(builder.equal(root.get("entityType"), ReportEntityType.FUNCTION), root.get("entityId").in(cards));
        };
    }

    /** Reports about cards whose responsible staff member is the given user. */
    private static Specification<InformationReport> assignedTo(Long userId) {
        return (root, criteria, builder) -> {
            Subquery<Long> cards = criteria.subquery(Long.class);
            Root<OrgFunction> card = cards.from(OrgFunction.class);
            cards.select(card.get("id")).where(builder.equal(card.get("assignedUserId"), userId));
            return builder.and(builder.equal(root.get("entityType"), ReportEntityType.FUNCTION), root.get("entityId").in(cards));
        };
    }

    private static Set<ReportStatus> allowedTargets(ReportStatus from) {
        Set<ReportStatus> targets = EnumSet.noneOf(ReportStatus.class);
        for (ReportStatus candidate : ReportStatus.values()) {
            if (from.canMoveTo(candidate)) {
                targets.add(candidate);
            }
        }
        return targets;
    }
}
