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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
    /** Lazily resolved: OrgFunctionService itself queues translations, so it cannot be a constructor dependency. */
    private final ObjectProvider<OrgFunctionService> revisions;

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
            result.put(function.getId(), summary(function, byId.get(function.getId()), active, automatic));
        }
        return result;
    }

    /**
     * Queues editable cards (and, for global staff, categories) that still lack translations in an active language
     * the provider supports. With {@code includePublished}, published cards with such gaps get a draft revision (the
     * existing one, or a new one opened as the calling staff member) that is queued instead: the public row never
     * changes, and the machine translations reach visitors only after the revision is reviewed and published.
     * Bounded by {@code limit}, never touches jobs that are already open, never calls the provider itself.
     */
    @Transactional
    public TranslationBackfillResponse backfill(Long organizationId, Integer requestedLimit) {
        return backfill(organizationId, requestedLimit, false);
    }

    @Transactional
    public TranslationBackfillResponse backfill(Long organizationId, Integer requestedLimit, boolean includePublished) {
        access.requireTranslationEditor();
        OrganizationScope scope = access.scope(organizationId);
        int limit = Math.max(1, Math.min(requestedLimit == null ? DEFAULT_BACKFILL_LIMIT : requestedLimit, properties.getBackfillMaxLimit()));
        Set<String> active = languages.activeLanguageCodes().orElseThrow(() ->
                new TranslationUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "Active languages are unavailable; retry later"));

        Backfill run = new Backfill(limit);
        Set<Long> openFunctions = new HashSet<>(jobs.findOpenTargetIds(TranslationTarget.FUNCTION));
        List<OrgFunction> candidates = inScope(scope, List.of(FunctionStatus.DRAFT, FunctionStatus.CHANGES_REQUESTED, FunctionStatus.DEACTIVATED));
        Set<Long> examinedDrafts = candidates.stream().map(OrgFunction::getId).collect(Collectors.toCollection(HashSet::new));
        for (OrgFunction function : candidates) {
            if (run.full()) {
                return run.response(true);
            }
            run.examined++;
            if (!needsMachineTranslation(function, active)) {
                continue;
            }
            if (openFunctions.contains(function.getId())) {
                run.alreadyQueued++;
            } else {
                enqueue(TranslationTarget.FUNCTION, function.getId());
                openFunctions.add(function.getId());
                run.queued++;
            }
        }
        if (includePublished) {
            for (OrgFunction published : inScope(scope, List.of(FunctionStatus.PUBLISHED))) {
                if (run.full()) {
                    return run.response(true);
                }
                if (published.isRevision()) {
                    continue;
                }
                run.examined++;
                backfillPublished(published, active, openFunctions, examinedDrafts, run);
            }
        }
        if (scope.allOrganizations()) {
            Set<Long> openCategories = jobs.findOpenTargetIds(TranslationTarget.CATEGORY);
            for (FunctionCategory category : categories.findAll()) {
                if (run.full()) {
                    return run.response(true);
                }
                run.examined++;
                if (supportedMissing(MissingTranslations.of(category, active), MissingTranslations.sourceLanguageOf(category)).isEmpty()) {
                    continue;
                }
                if (openCategories.contains(category.getId())) {
                    run.alreadyQueued++;
                } else {
                    enqueue(TranslationTarget.CATEGORY, category.getId());
                    run.queued++;
                }
            }
        }
        return run.response(false);
    }

    private void backfillPublished(OrgFunction published, Set<String> active, Set<Long> openFunctions,
                                   Set<Long> examinedDrafts, Backfill run) {
        Optional<OrgFunction> existing = functions.findByRevisionOfId(published.getId());
        if (existing.isPresent()) {
            OrgFunction revision = existing.get();
            if (examinedDrafts.contains(revision.getId())) {
                return; // a draft revision was already handled with the other drafts above
            }
            if (revision.getStatus() == FunctionStatus.PENDING_REVIEW) {
                if (needsMachineTranslation(revision, active)) {
                    run.skippedUnderReview++;
                }
                return;
            }
            if (!revision.isEditable() || !needsMachineTranslation(revision, active)) {
                return;
            }
            if (openFunctions.contains(revision.getId())) {
                run.alreadyQueued++;
                return;
            }
            enqueue(TranslationTarget.FUNCTION, revision.getId());
            openFunctions.add(revision.getId());
            run.queued++;
            run.revisionsQueued++;
            return;
        }
        if (!needsMachineTranslation(published, active)) {
            return;
        }
        // Same code path as "Edit" on a published card: organization scope check and REVISION_CREATE audit entry.
        OrgFunction revision = revisions.getObject().createRevision(published.getId());
        enqueue(TranslationTarget.FUNCTION, revision.getId());
        openFunctions.add(revision.getId());
        run.queued++;
        run.revisionsCreated++;
    }

    private List<OrgFunction> inScope(OrganizationScope scope, List<FunctionStatus> statuses) {
        List<OrgFunction> result = new ArrayList<>();
        for (FunctionStatus status : statuses) {
            result.addAll(scope.allOrganizations()
                    ? functions.findAllByStatus(status)
                    : functions.findAllByStatusAndOrganizationIdIn(status, scope.organizationIds()));
        }
        result.sort(Comparator.comparing(OrgFunction::getId));
        return result;
    }

    /** True when an active language the provider can produce is still missing; unsupported gaps need a human. */
    private boolean needsMachineTranslation(OrgFunction function, Set<String> active) {
        return !supportedMissing(MissingTranslations.of(function, active), function.getSourceLanguage()).isEmpty();
    }

    private List<String> supportedMissing(List<String> missing, String source) {
        if (missing.isEmpty()) {
            return missing;
        }
        if (translator.sourceUnsupported(MissingTranslations.normalize(source))) {
            return List.of();
        }
        Set<String> unsupported = translator.unsupportedTargets(MissingTranslations.normalize(source), missing);
        return missing.stream().filter(code -> !unsupported.contains(code)).toList();
    }

    private MachineTranslationSummary summary(OrgFunction function, TranslationJob job) {
        return summary(function, job, languages.activeLanguageCodes(), automatic());
    }

    private MachineTranslationSummary summary(OrgFunction function, TranslationJob job, Optional<Set<String>> active,
                                              boolean automatic) {
        List<String> missing = active.map(codes -> MissingTranslations.of(function, codes)).orElse(List.of());
        List<String> unsupported = active.map(codes -> {
            List<String> targets = MissingTranslations.targets(codes, function.getSourceLanguage());
            Set<String> refused = translator.unsupportedTargets(MissingTranslations.normalize(function.getSourceLanguage()), targets);
            return targets.stream().filter(refused::contains).toList();
        }).orElse(List.of());
        return MachineTranslationSummary.of(automatic, job, missing, unsupported);
    }

    private static final class Backfill {
        private final int limit;
        private int examined;
        private int queued;
        private int alreadyQueued;
        private int revisionsCreated;
        private int revisionsQueued;
        private int skippedUnderReview;

        private Backfill(int limit) {
            this.limit = limit;
        }

        private boolean full() {
            return queued >= limit;
        }

        private TranslationBackfillResponse response(boolean limitReached) {
            return new TranslationBackfillResponse(examined, queued, alreadyQueued, limit, limitReached,
                    revisionsCreated, revisionsQueued, skippedUnderReview);
        }
    }
}
