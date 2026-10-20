package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.MachineTranslationProperties;
import adliya.uz.functioncatalogservice.dto.MachineTranslationSummary;
import adliya.uz.functioncatalogservice.dto.TranslationBackfillResponse;
import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslationJob;
import adliya.uz.functioncatalogservice.entity.TranslationJobStatus;
import adliya.uz.functioncatalogservice.entity.TranslationTarget;
import adliya.uz.functioncatalogservice.exception.TranslationUnavailableException;
import adliya.uz.functioncatalogservice.repository.FunctionCategoryRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.TranslationJobRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entry point of the background machine translation: records content that may need translations and reports the
 * state of that work to staff. Nothing here talks to the provider; {@link MachineTranslationWorker} does, later.
 */
@Service
@RequiredArgsConstructor
public class MachineTranslationQueue {

    private static final int DEFAULT_BACKFILL_LIMIT = 50;

    private final TranslationJobRepository jobs;
    private final OrgFunctionRepository functions;
    private final FunctionCategoryRepository categories;
    private final ActiveLanguageClient languages;
    private final TranslationClient translator;
    private final MachineTranslationProperties properties;
    private final CatalogAccess access;

    /** True when a provider is configured and the worker is switched on. */
    public boolean automatic() {
        return properties.isEnabled() && translator.isAvailable();
    }

    /** Queues (or re-queues) the record; the worker later fills only translations that are still missing. */
    @Transactional
    public TranslationJob enqueue(TranslationTarget type, Long id) {
        Instant now = Instant.now();
        TranslationJob job = jobs.findByTargetTypeAndTargetId(type, id).orElseGet(() -> TranslationJob.builder()
                .targetType(type).targetId(id).requestedAt(now).build());
        job.setStatus(TranslationJobStatus.PENDING);
        job.setAttempts(0);
        job.setRequestedAt(now);
        job.setNextAttemptAt(now);
        job.setFinishedAt(null);
        job.setLastError(null);
        return jobs.saveAndFlush(job);
    }

    /** Queues the record only when it has never been queued; used by idempotent paths such as seeding. */
    @Transactional
    public void enqueueIfAbsent(TranslationTarget type, Long id) {
        if (jobs.findByTargetTypeAndTargetId(type, id).isEmpty()) {
            enqueue(type, id);
        }
    }

    /** Puts a job that was skipped or had given up back on the queue (e.g. a card returned to DRAFT). */
    @Transactional
    public void resume(TranslationTarget type, Long id) {
        jobs.findByTargetTypeAndTargetId(type, id)
                .filter(job -> job.getStatus() == TranslationJobStatus.SKIPPED || job.getStatus() == TranslationJobStatus.FAILED)
                .ifPresent(job -> enqueue(type, id));
    }

    @Transactional
    public void remove(TranslationTarget type, Long id) {
        jobs.deleteByTargetTypeAndTargetId(type, id);
    }

    /** Explicit re-queue by staff of their own organization's draft; the worker handles it on the next tick. */
    @Transactional
    public MachineTranslationSummary retry(Long functionId) {
        access.requireTranslationEditor();
        OrgFunction function = FunctionAutoTranslationService.requireTranslatable(access, functions.findById(functionId)
                .orElseThrow(() -> new NoSuchElementException("Function not found, ID: " + functionId)));
        return summary(function, enqueue(TranslationTarget.FUNCTION, functionId));
    }

    @Transactional(readOnly = true)
    public MachineTranslationSummary summary(OrgFunction function) {
        return summary(function, jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, function.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Map<Long, MachineTranslationSummary> summaries(Collection<OrgFunction> records) {
        if (records.isEmpty()) {
            return Map.of();
        }
        Map<Long, TranslationJob> byId = jobs.findAllByTargetTypeAndTargetIdIn(TranslationTarget.FUNCTION,
                        records.stream().map(OrgFunction::getId).toList()).stream()
                .collect(Collectors.toMap(TranslationJob::getTargetId, Function.identity()));
        Optional<Set<String>> active = languages.activeLanguageCodes();
        boolean automatic = automatic();
        Map<Long, MachineTranslationSummary> result = new LinkedHashMap<>();
        for (OrgFunction function : records) {
            result.put(function.getId(), MachineTranslationSummary.of(automatic, byId.get(function.getId()),
                    active.map(codes -> MissingTranslations.of(function, codes)).orElse(List.of())));
        }
        return result;
    }

    /**
     * Queues editable cards (and, for global staff, categories) that still lack translations in an active language.
     * Bounded by {@code limit}, never touches jobs that are already open, never calls the provider itself.
     */
    @Transactional
    public TranslationBackfillResponse backfill(Long organizationId, Integer requestedLimit) {
        access.requireTranslationEditor();
        OrganizationScope scope = access.scope(organizationId);
        int limit = Math.max(1, Math.min(requestedLimit == null ? DEFAULT_BACKFILL_LIMIT : requestedLimit, properties.getBackfillMaxLimit()));
        Set<String> active = languages.activeLanguageCodes().orElseThrow(() ->
                new TranslationUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "Active languages are unavailable; retry later"));

        List<OrgFunction> candidates = new ArrayList<>();
        for (FunctionStatus status : List.of(FunctionStatus.DRAFT, FunctionStatus.DEACTIVATED)) {
            candidates.addAll(scope.allOrganizations()
                    ? functions.findAllByStatus(status)
                    : functions.findAllByStatusAndOrganizationIdIn(status, scope.organizationIds()));
        }
        candidates.sort(Comparator.comparing(OrgFunction::getId));
        Set<Long> openFunctions = jobs.findOpenTargetIds(TranslationTarget.FUNCTION);
        int examined = 0;
        int queued = 0;
        int alreadyQueued = 0;
        for (OrgFunction function : candidates) {
            if (queued >= limit) {
                return new TranslationBackfillResponse(examined, queued, alreadyQueued, limit, true);
            }
            examined++;
            if (MissingTranslations.of(function, active).isEmpty()) {
                continue;
            }
            if (openFunctions.contains(function.getId())) {
                alreadyQueued++;
            } else {
                enqueue(TranslationTarget.FUNCTION, function.getId());
                queued++;
            }
        }
        if (scope.allOrganizations()) {
            Set<Long> openCategories = jobs.findOpenTargetIds(TranslationTarget.CATEGORY);
            for (FunctionCategory category : categories.findAll()) {
                if (queued >= limit) {
                    return new TranslationBackfillResponse(examined, queued, alreadyQueued, limit, true);
                }
                examined++;
                if (MissingTranslations.of(category, active).isEmpty()) {
                    continue;
                }
                if (openCategories.contains(category.getId())) {
                    alreadyQueued++;
                } else {
                    enqueue(TranslationTarget.CATEGORY, category.getId());
                    queued++;
                }
            }
        }
        return new TranslationBackfillResponse(examined, queued, alreadyQueued, limit, false);
    }

    private MachineTranslationSummary summary(OrgFunction function, TranslationJob job) {
        List<String> missing = languages.activeLanguageCodes()
                .map(codes -> MissingTranslations.of(function, codes))
                .orElse(List.of());
        return MachineTranslationSummary.of(automatic(), job, missing);
    }
}
