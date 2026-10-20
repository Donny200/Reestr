package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.ReportProperties;
import adliya.uz.functioncatalogservice.dto.SubmitReportRequest;
import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ReportCategory;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.entity.ReportStatus;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import adliya.uz.functioncatalogservice.repository.InformationReportRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReportSubmissionService {

    private static final Duration DEDUPLICATION_WINDOW = Duration.ofDays(1);
    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int LABEL_MAX_LENGTH = 150;

    private final ReportRateLimiter rateLimiter;
    private final ReportProperties properties;
    private final OrgFunctionRepository functions;
    private final IdentityOrganizationClient organizations;
    private final InformationReportRepository reports;
    private final AuditWriter audit;
    private final TransactionTemplate transaction;

    public void submit(SubmitReportRequest request, String clientAddress) {
        rateLimiter.acquire(clientAddress);
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }
        if (!request.category().appliesTo(request.entityType())) {
            throw new IllegalArgumentException("Category " + request.category() + " does not apply to " + request.entityType());
        }
        String description = normalizedDescription(request.description());
        Target target = resolve(request.entityType(), request.entityId());
        transaction.executeWithoutResult(status -> store(request.entityType(), request.entityId(), request.category(),
                request.contact(), request.language(), target, description));
    }

    /**
     * A report of incorrect information sent from the support chat (the chat message is the visitor's only channel);
     * runs in the caller's transaction so the message and the report are stored together. Returns null when the same
     * open report already exists.
     */
    public InformationReport fileFromSupport(ReportEntityType type, Long entityId, ReportCategory category, String text,
                                             String language, String clientAddress) {
        rateLimiter.acquire(clientAddress);
        if (!category.appliesTo(type)) {
            throw new InvalidFieldException("reportCategory", "Category " + category + " does not apply to " + type);
        }
        return store(type, entityId, category, null, language, resolve(type, entityId), normalizedDescription(text));
    }

    private InformationReport store(ReportEntityType type, Long entityId, ReportCategory category, String contact, String language,
                                    Target target, String description) {
        Instant now = Instant.now();
        Instant since = now.minus(DEDUPLICATION_WINDOW);
        if (reports.countByEntityTypeAndEntityIdAndCreatedAtAfter(type, entityId, since) >= properties.getPerEntityDailyLimit()) {
            throw new RateLimitExceededException(DEDUPLICATION_WINDOW);
        }
        if (reports.existsByEntityTypeAndEntityIdAndDescriptionAndStatusInAndCreatedAtAfter(
                type, entityId, description, ReportStatus.OPEN, since)) {
            return null;
        }
        InformationReport report = reports.save(InformationReport.builder()
                .entityType(type)
                .entityId(entityId)
                .entityLabel(TextValues.truncate(target.label(), LABEL_MAX_LENGTH))
                .organizationId(target.organizationId())
                .category(category)
                .description(description)
                .contact(contact == null || contact.isBlank() ? null : contact.trim())
                .language(TextValues.language(language))
                .status(ReportStatus.NEW)
                .createdAt(now)
                .updatedAt(now)
                .build());
        audit.reportReceived(report);
        return report;
    }

    private Target resolve(ReportEntityType type, Long id) {
        if (type == ReportEntityType.FUNCTION) {
            OrgFunction function = functions.requirePublished(id);
            return new Target(function.getName(), function.getOrganizationId());
        }
        IdentityOrganizationClient.PublicOrganization organization = organizations.requirePublic(id);
        return new Target(organization.name() == null ? "#" + id : organization.name(), organization.id());
    }

    private static String normalizedDescription(String raw) {
        String value = VisitorText.normalize(raw);
        if (!VisitorText.meaningful(value, MIN_DESCRIPTION_LENGTH)) {
            throw new IllegalArgumentException("Describe the problem in at least " + MIN_DESCRIPTION_LENGTH + " characters");
        }
        if (VisitorText.links(value) > VisitorText.MAX_LINKS) {
            throw new IllegalArgumentException("A report may contain at most " + VisitorText.MAX_LINKS + " links");
        }
        return value;
    }

    private record Target(String label, Long organizationId) {}
}
