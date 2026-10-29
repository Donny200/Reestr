package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.QualityIssue;
import adliya.uz.functioncatalogservice.dto.QualityQueue;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.entity.VerificationStatus;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceQualityService {

    private static final List<FunctionStatus> QUEUE_ORDER = List.of(
            FunctionStatus.PUBLISHED, FunctionStatus.PENDING_REVIEW, FunctionStatus.CHANGES_REQUESTED, FunctionStatus.DRAFT,
            FunctionStatus.DEACTIVATED);
    private static final Comparator<QualityQueue.Item> ORDER = Comparator
            .comparingInt((QualityQueue.Item item) -> QUEUE_ORDER.indexOf(item.status()))
            .thenComparing(Comparator.comparingInt((QualityQueue.Item item) -> item.issues().size()).reversed())
            .thenComparing(QualityQueue.Item::functionId);

    private final OrgFunctionRepository functions;
    private final ActiveLanguageClient languages;
    private final CatalogAccess access;

    public QualityQueue queue(Long organizationId, Long categoryId, QualityIssueType issue, FunctionStatus status) {
        OrganizationScope scope = access.scope(organizationId);
        Optional<Set<String>> active = languages.activeLanguageCodes();
        Instant now = Instant.now();
        List<QualityQueue.Item> items = inScope(scope, categoryId).stream()
                .filter(function -> status == null || function.getStatus() == status)
                .map(function -> item(function, ServiceQualityRules.evaluate(function, active, now), now))
                .filter(item -> !item.issues().isEmpty())
                .filter(item -> issue == null || item.issues().stream().anyMatch(found -> found.type() == issue))
                .sorted(ORDER)
                .toList();
        return new QualityQueue(active.isPresent(), sorted(active), items);
    }

    public Snapshot snapshot(OrganizationScope scope, Long categoryId) {
        Optional<Set<String>> active = languages.activeLanguageCodes();
        Instant now = Instant.now();
        Map<FunctionStatus, Long> statuses = new EnumMap<>(FunctionStatus.class);
        Map<QualityIssueType, Long> attention = new EnumMap<>(QualityIssueType.class);
        for (FunctionStatus status : FunctionStatus.values()) {
            statuses.put(status, 0L);
        }
        for (QualityIssueType type : QualityIssueType.values()) {
            attention.put(type, 0L);
        }
        List<OrgFunction> cards = inScope(scope, categoryId);
        long needingAttention = 0;
        for (OrgFunction card : cards) {
            statuses.merge(card.getStatus(), 1L, Long::sum);
            List<QualityIssue> issues = ServiceQualityRules.evaluate(card, active, now);
            if (!issues.isEmpty()) {
                needingAttention++;
            }
            issues.forEach(issue -> attention.merge(issue.type(), 1L, Long::sum));
        }
        Set<Long> ids = cards.stream().map(OrgFunction::getId).collect(Collectors.toUnmodifiableSet());
        return new Snapshot(statuses, needingAttention, attention, active.isPresent(), sorted(active), ids);
    }

    List<OrgFunction> inScope(OrganizationScope scope, Long categoryId) {
        if (scope.empty()) {
            return List.of();
        }
        List<OrgFunction> cards = (scope.allOrganizations()
                ? functions.findAll()
                : functions.findAllByOrganizationIdIn(scope.organizationIds())).stream()
                .filter(card -> !card.isRevision())
                .toList();
        return categoryId == null ? cards : cards.stream()
                .filter(card -> card.getFunctionCategory() != null && categoryId.equals(card.getFunctionCategory().getId()))
                .toList();
    }

    private static QualityQueue.Item item(OrgFunction function, List<QualityIssue> issues, Instant now) {
        Instant lastVerified = function.getLastVerifiedAt();
        return new QualityQueue.Item(function.getId(), function.getName(), function.getNameTranslations(), function.getStatus(),
                function.getOrganizationId(),
                function.getFunctionCategory() == null ? null : function.getFunctionCategory().getId(),
                function.getCategory(), function.verificationStatus(now), lastVerified,
                lastVerified == null ? null : lastVerified.plus(VerificationStatus.RECHECK_AFTER),
                function.getOfficialSourceUrl(), issues);
    }

    private static List<String> sorted(Optional<Set<String>> codes) {
        return codes.map(values -> values.stream().sorted().toList()).orElse(List.of());
    }

    public record Snapshot(Map<FunctionStatus, Long> statuses, long servicesNeedingAttention,
                           Map<QualityIssueType, Long> attention, boolean translationsChecked,
                           List<String> activeLanguages, Set<Long> functionIds) {}
}
