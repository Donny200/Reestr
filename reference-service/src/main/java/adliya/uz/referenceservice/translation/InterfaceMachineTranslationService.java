package adliya.uz.referenceservice.translation;

import adliya.uz.referenceservice.catalog.SourceHash;
import adliya.uz.referenceservice.config.CacheConfig;
import adliya.uz.referenceservice.config.InterfaceTranslationProperties;
import adliya.uz.referenceservice.entity.InterfaceTranslationJob;
import adliya.uz.referenceservice.entity.InterfaceTranslationJobStatus;
import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.entity.TranslationSource;
import adliya.uz.referenceservice.repository.InterfaceTranslationJobRepository;
import adliya.uz.referenceservice.repository.TranslationKeyRepository;
import adliya.uz.referenceservice.repository.TranslationRepository;
import adliya.uz.referenceservice.service.InterfaceCoverageService;
import adliya.uz.referenceservice.service.InterfaceCoverageService.LanguageCoverage;
import adliya.uz.referenceservice.service.InterfaceTranslationDictionaryService;
import adliya.uz.referenceservice.service.LanguageTagNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fills interface dictionary entries that a language is missing with Azure Translator, persistently and in batches.
 *
 * <ul>
 *     <li>{@link #scan} queues a job for every active language that has gaps (new language, new keys, or machine values
 *     written for an older English text). It never calls the provider.</li>
 *     <li>{@link #runDue} processes due jobs: maps the language to its provider code (unsupported languages are reported
 *     as UNSUPPORTED and never block other languages), sends the missing English texts in bounded batches outside any
 *     database transaction, and stores each batch as soon as it returns, so a retry continues where the last one
 *     stopped. Only keys that are still missing (or stale machine values) are written: human and curated values are
 *     never replaced.</li>
 *     <li>Failures back off (1, 5, 15, 60 minutes, then 6 hours) and become FAILED after the configured attempts;
 *     {@link #requeue} is the explicit retry.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterfaceMachineTranslationService {

    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(6));
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMinutes(5);

    private final InterfaceTranslationJobRepository jobs;
    private final TranslationKeyRepository keyRepository;
    private final TranslationRepository translationRepository;
    private final InterfaceCoverageService coverageService;
    private final ActiveLanguagesClient activeLanguages;
    private final AzureLanguageSupport languageSupport;
    private final AzureInterfaceTranslator translator;
    private final InterfaceTranslationProperties properties;
    private final TransactionTemplate transaction;
    private final CacheManager cacheManager;
    private final LanguageTagNormalizer normalizer;

    /** True when the provider is configured and the worker is switched on. */
    public boolean automatic() {
        return properties.isEnabled() && translator.configured();
    }

    /** Queues every active language with gaps; returns the languages queued now. Does not call the provider. */
    public List<String> scan(Instant now) {
        Set<String> active = activeLanguages.activeLanguageCodes().orElse(Set.of());
        if (active.isEmpty()) {
            return List.of();
        }
        Map<String, LanguageCoverage> coverage = coverageService.coverage(active);
        List<String> queued = new ArrayList<>();
        transaction.executeWithoutResult(status -> {
            Map<String, InterfaceTranslationJob> existing = jobs.findAllByLanguageCodeIn(active).stream()
                    .collect(Collectors.toMap(InterfaceTranslationJob::getLanguageCode, Function.identity()));
            coverage.forEach((code, languageCoverage) -> {
                int gaps = languageCoverage.keysToTranslate().size();
                if (languageCoverage.englishBase() || gaps == 0) {
                    return;
                }
                InterfaceTranslationJob job = existing.get(code);
                boolean supportChanged = job != null && job.getStatus() == InterfaceTranslationJobStatus.UNSUPPORTED
                        && languageSupport.providerCode(code).isPresent();
                if (job == null || job.getStatus() == InterfaceTranslationJobStatus.DONE || supportChanged) {
                    jobs.save(reset(job == null ? InterfaceTranslationJob.builder().languageCode(code).build() : job, gaps, now));
                    queued.add(code);
                }
            });
        });
        return queued;
    }

    /** Explicit retry from the admin console: queues the language now, whatever its previous state. */
    public InterfaceTranslationJob requeue(String rawLanguageCode, Instant now) {
        String code = normalizer.normalize(rawLanguageCode);
        Set<String> active = activeLanguages.activeLanguageCodes().orElse(null);
        if (active != null && active.stream().noneMatch(code::equalsIgnoreCase)) {
            throw new NoSuchElementException("Language is not active: " + code);
        }
        LanguageCoverage coverage = coverageService.coverage(code);
        return Objects.requireNonNull(transaction.execute(status -> {
            InterfaceTranslationJob job = jobs.findByLanguageCode(code)
                    .orElseGet(() -> InterfaceTranslationJob.builder().languageCode(code).build());
            return jobs.save(reset(job, coverage.keysToTranslate().size(), now));
        }));
    }

    public Optional<InterfaceTranslationJob> job(String languageCode) {
        return jobs.findByLanguageCode(languageCode);
    }

    public Map<String, InterfaceTranslationJob> jobs(Set<String> languageCodes) {
        return jobs.findAllByLanguageCodeIn(languageCodes).stream()
                .collect(Collectors.toMap(InterfaceTranslationJob::getLanguageCode, Function.identity()));
    }

    /** Handles up to {@code batch-size} due jobs. Safe to call from tests with a fixed clock. */
    public RunResult runDue(Instant now) {
        List<InterfaceTranslationJob> claimed = claim(now);
        int done = 0;
        int retried = 0;
        int unsupported = 0;
        int failed = 0;
        for (InterfaceTranslationJob job : claimed) {
            switch (process(job.getLanguageCode(), now)) {
                case DONE -> done++;
                case RETRY -> retried++;
                case UNSUPPORTED -> unsupported++;
                case FAILED -> failed++;
            }
        }
        if (!claimed.isEmpty()) {
            evictDictionaries();
        }
        return new RunResult(claimed.size(), done, retried, unsupported, failed);
    }

    private List<InterfaceTranslationJob> claim(Instant now) {
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                List<InterfaceTranslationJob> due = jobs.findDue(now, PageRequest.of(0, Math.max(1, properties.getBatchSize())));
                for (InterfaceTranslationJob job : due) {
                    job.setStatus(InterfaceTranslationJobStatus.RUNNING);
                    job.setAttempts(job.getAttempts() + 1);
                    job.setStartedAt(now);
                    job.setNextAttemptAt(now.plus(properties.getLease()));
                }
                return jobs.saveAllAndFlush(due);
            }));
        } catch (ObjectOptimisticLockingFailureException exception) {
            return List.of(); // another instance claimed them first
        }
    }

    Outcome process(String code, Instant now) {
        Optional<String> provider = languageSupport.providerCode(code);
        if (provider.isEmpty()) {
            finish(code, InterfaceTranslationJobStatus.UNSUPPORTED, null, now,
                    "Azure Translator has no language code for " + code + "; add these translations manually", 0, 0);
            return Outcome.UNSUPPORTED;
        }
        LanguageCoverage coverage = coverageService.coverage(code);
        List<String> keys = coverage.keysToTranslate().stream()
                .filter(coverage.englishByKey()::containsKey)
                .toList();
        int translated = 0;
        int rejected = 0;
        try {
            for (List<String> batch : batches(keys, coverage.englishByKey())) {
                List<String> english = batch.stream().map(coverage.englishByKey()::get).toList();
                List<String> answers = translator.translate(english, provider.get());
                Map<String, String> accepted = new LinkedHashMap<>();
                for (int i = 0; i < batch.size(); i++) {
                    String answer = i < answers.size() ? answers.get(i) : null;
                    if (answer == null) {
                        rejected++;
                    } else {
                        accepted.put(batch.get(i), answer);
                    }
                }
                translated += store(code, accepted, coverage.englishByKey(), now);
            }
        } catch (ProviderException exception) {
            return fail(code, provider.get(), now, exception, translated);
        }
        String note = rejected == 0 ? null
                : rejected + " text(s) were not accepted (placeholders or markup changed); translate them manually or retry";
        finish(code, InterfaceTranslationJobStatus.DONE, provider.get(), now, note, keys.size(), translated);
        return Outcome.DONE;
    }

    /** Writes machine values for keys that are still missing or still hold a stale machine value; returns how many. */
    private int store(String code, Map<String, String> values, Map<String, String> englishByKey, Instant now) {
        if (values.isEmpty()) {
            return 0;
        }
        return Objects.requireNonNull(transaction.execute(status -> {
            Map<String, TranslationKey> keys = keyRepository.findAllByTranslationKeyInAndActiveTrue(values.keySet()).stream()
                    .collect(Collectors.toMap(TranslationKey::getTranslationKey, Function.identity()));
            List<Long> ids = keys.values().stream().map(TranslationKey::getId).toList();
            Map<Long, Translation> existing = translationRepository.findAllByLanguageAndKeyIds(code, ids).stream()
                    .collect(Collectors.toMap(item -> item.getTranslationKey().getId(), Function.identity()));
            Map<Long, String> currentEnglish = translationRepository
                    .findAllByLanguageAndKeyIds(InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE, ids).stream()
                    .collect(Collectors.toMap(item -> item.getTranslationKey().getId(), Translation::getTranslationValue));
            List<Translation> changed = new ArrayList<>();
            values.forEach((name, value) -> {
                TranslationKey key = keys.get(name);
                if (key == null || !Objects.equals(currentEnglish.get(key.getId()), englishByKey.get(name))) {
                    return; // key retired or English changed while translating: the next scan queues it again
                }
                String hash = SourceHash.of(englishByKey.get(name));
                Translation row = existing.get(key.getId());
                if (row == null) {
                    row = Translation.builder().translationKey(key).languageCode(code).build();
                } else if (row.getSource() != TranslationSource.MACHINE || hash.equals(row.getSourceHash())) {
                    return; // a person or the catalog filled it meanwhile, or it is already current: never replace
                }
                row.setTranslationValue(value);
                row.setSource(TranslationSource.MACHINE);
                row.setSourceHash(hash);
                row.setUpdatedAt(now);
                changed.add(row);
            });
            translationRepository.saveAll(changed);
            return changed.size();
        }));
    }

    private List<List<String>> batches(List<String> keys, Map<String, String> englishByKey) {
        List<List<String>> result = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int characters = 0;
        for (String key : keys) {
            int length = InterfaceTextProtection.protect(englishByKey.get(key)).length();
            if (!current.isEmpty() && (current.size() >= properties.getTextsPerRequest()
                    || characters + length > properties.getCharactersPerRequest())) {
                result.add(current);
                current = new ArrayList<>();
                characters = 0;
            }
            current.add(key);
            characters += length;
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    private Outcome fail(String code, String provider, Instant now, ProviderException exception, int translated) {
        return Objects.requireNonNull(transaction.execute(status -> {
            InterfaceTranslationJob job = jobs.findByLanguageCode(code).orElseThrow();
            job.setProviderCode(provider);
            job.setTranslatedKeys(job.getTranslatedKeys() + translated);
            if (exception.rejected()) {
                // Azure refused the language itself (its list changed since it was fetched): report, do not retry.
                job.setStatus(InterfaceTranslationJobStatus.UNSUPPORTED);
                job.setFinishedAt(now);
                job.setLastError(shorten("Azure Translator refused language " + provider + " for " + code + " ("
                        + exception.getMessage() + ")"));
                jobs.save(job);
                return Outcome.UNSUPPORTED;
            }
            if (job.getAttempts() >= properties.getMaxAttempts()) {
                job.setStatus(InterfaceTranslationJobStatus.FAILED);
                job.setFinishedAt(now);
                job.setLastError(shorten("Gave up after " + job.getAttempts() + " attempts: " + exception.getMessage()));
                jobs.save(job);
                return Outcome.FAILED;
            }
            Duration delay = BACKOFF.get(Math.min(Math.max(job.getAttempts() - 1, 0), BACKOFF.size() - 1));
            if (exception.rateLimited() && delay.compareTo(RATE_LIMIT_BACKOFF) < 0) {
                delay = RATE_LIMIT_BACKOFF;
            }
            job.setStatus(InterfaceTranslationJobStatus.PENDING);
            job.setNextAttemptAt(now.plus(delay));
            job.setLastError(shorten(exception.getMessage() + "; next attempt scheduled"));
            jobs.save(job);
            return Outcome.RETRY;
        }));
    }

    private void finish(String code, InterfaceTranslationJobStatus status, String provider, Instant now, String note,
                        int missing, int translated) {
        transaction.executeWithoutResult(tx -> {
            InterfaceTranslationJob job = jobs.findByLanguageCode(code).orElseThrow();
            job.setStatus(status);
            job.setProviderCode(provider);
            job.setFinishedAt(now);
            job.setNextAttemptAt(now);
            job.setMissingKeys(missing);
            job.setTranslatedKeys(job.getTranslatedKeys() + translated);
            job.setLastError(shorten(note));
            jobs.save(job);
        });
    }

    private static InterfaceTranslationJob reset(InterfaceTranslationJob job, int missing, Instant now) {
        job.setStatus(InterfaceTranslationJobStatus.PENDING);
        job.setAttempts(0);
        job.setMissingKeys(missing);
        job.setTranslatedKeys(0);
        job.setRequestedAt(now);
        job.setNextAttemptAt(now);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setLastError(null);
        return job;
    }

    private void evictDictionaries() {
        Cache cache = cacheManager.getCache(CacheConfig.DICTIONARY_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    private static String shorten(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 497) + "...";
    }

    enum Outcome { DONE, RETRY, UNSUPPORTED, FAILED }

    public record RunResult(int claimed, int done, int retried, int unsupported, int failed) {}
}
