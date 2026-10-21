package adliya.uz.referenceservice.catalog;

import adliya.uz.referenceservice.config.CacheConfig;
import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.entity.TranslationSource;
import adliya.uz.referenceservice.repository.TranslationKeyRepository;
import adliya.uz.referenceservice.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Brings the database dictionary in line with the shipped {@link InterfaceCatalog} on start-up, in one transaction
 * guarded by a PostgreSQL advisory lock so that several instances can start together.
 *
 * <p>Data-preservation rules: a HUMAN value is never changed; a SEED value follows the catalog; a MACHINE value is
 * replaced by a curated catalog value when one exists; a missing value is inserted as SEED. Rows written before
 * provenance existed ({@code source IS NULL}) become SEED when they equal the catalog value and HUMAN otherwise, so
 * earlier administrator edits survive. Catalog keys are (re)activated; keys that the catalog used to own and no longer
 * lists are deactivated, never deleted. Keys created by administrators are left alone.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class InterfaceCatalogSynchronizer implements ApplicationRunner {

    /** Arbitrary constant shared by all instances of this service. */
    static final long ADVISORY_LOCK_ID = 7_265_621_002L;

    private final InterfaceCatalog catalog;
    private final TranslationKeyRepository keyRepository;
    private final TranslationRepository translationRepository;
    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final CacheManager cacheManager;

    @Value("${interface.catalog.sync-on-startup:true}")
    private boolean syncOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!syncOnStartup) {
            return;
        }
        Result result = synchronize(Instant.now());
        log.info("Interface catalog synchronized: {} key(s) added, {} reactivated, {} retired; {} value(s) inserted, "
                        + "{} updated, {} machine value(s) replaced by curated ones, {} earlier value(s) that differ from the "
                        + "catalog kept as human edits, {} earlier value(s) outside the catalog kept as human edits",
                result.keysAdded(), result.keysReactivated(), result.keysRetired(), result.valuesInserted(),
                result.valuesUpdated(), result.machineReplaced(), result.legacyKeptAsHuman(), result.legacyOutsideCatalog());
    }

    public Result synchronize(Instant now) {
        Result result = Objects.requireNonNull(transaction.execute(status -> {
            lock();
            return apply(now);
        }));
        Cache cache = cacheManager.getCache(CacheConfig.DICTIONARY_CACHE);
        if (cache != null) {
            cache.clear();
        }
        return result;
    }

    private void lock() {
        try {
            jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?)) AS locked", Integer.class, ADVISORY_LOCK_ID);
        } catch (DataAccessException exception) {
            // Not PostgreSQL (e.g. an embedded test database): a single instance needs no lock.
            log.debug("Advisory lock unavailable: {}", exception.getMessage());
        }
    }

    private Result apply(Instant now) {
        Counter counter = new Counter();
        Map<String, TranslationKey> keys = syncKeys(counter);

        Map<String, Map<String, Translation>> byLanguage = new HashMap<>();
        for (Translation translation : translationRepository.findAllWithKeys()) {
            byLanguage.computeIfAbsent(translation.getLanguageCode(), ignored -> new HashMap<>())
                    .put(translation.getTranslationKey().getTranslationKey(), translation);
        }

        List<Translation> changed = new ArrayList<>();
        // English first: its effective value (catalog, or a human edit of it) is what other languages translate.
        syncLanguage(InterfaceCatalog.SOURCE_LANGUAGE, keys, byLanguage, Map.of(), now, changed, counter);
        Map<String, String> englishHashes = byLanguage.getOrDefault(InterfaceCatalog.SOURCE_LANGUAGE, Map.of())
                .values().stream()
                .collect(Collectors.toMap(item -> item.getTranslationKey().getTranslationKey(),
                        item -> SourceHash.of(item.getTranslationValue())));
        for (String language : catalog.languages()) {
            if (!InterfaceCatalog.SOURCE_LANGUAGE.equals(language)) {
                syncLanguage(language, keys, byLanguage, englishHashes, now, changed, counter);
            }
        }
        // Rows of other languages, and of keys the catalog does not own, that predate provenance: keep as human edits.
        byLanguage.values().forEach(rows -> rows.values().forEach(row -> {
            if (row.getSource() == null) {
                row.setSource(TranslationSource.HUMAN);
                row.setSourceHash(InterfaceCatalog.SOURCE_LANGUAGE.equals(row.getLanguageCode())
                        ? SourceHash.of(row.getTranslationValue())
                        : englishHashes.get(row.getTranslationKey().getTranslationKey()));
                row.setUpdatedAt(now);
                changed.add(row);
                counter.legacyOutsideCatalog++;
            }
        }));
        translationRepository.saveAll(new LinkedHashSet<>(changed));
        return counter.result();
    }

    private Map<String, TranslationKey> syncKeys(Counter counter) {
        Map<String, TranslationKey> existing = keyRepository.findAll().stream()
                .collect(Collectors.toMap(TranslationKey::getTranslationKey, Function.identity()));
        List<TranslationKey> changed = new ArrayList<>();
        Set<String> catalogKeys = new LinkedHashSet<>(catalog.keys());
        catalogKeys.addAll(catalog.pluralForms());
        for (String name : catalogKeys) {
            boolean required = !catalog.pluralForms().contains(name);
            TranslationKey key = existing.get(name);
            if (key == null) {
                key = TranslationKey.builder().translationKey(name).required(required).active(true).managed(true).build();
                existing.put(name, key);
                changed.add(key);
                counter.keysAdded++;
            } else if (!key.isActive() || !key.isManaged() || (!required && key.isRequired())) {
                if (!key.isActive()) {
                    counter.keysReactivated++;
                }
                key.setActive(true);
                key.setManaged(true);
                if (!required) {
                    key.setRequired(false);
                }
                changed.add(key);
            }
        }
        for (TranslationKey key : existing.values()) {
            if (key.isManaged() && key.isActive() && !catalogKeys.contains(key.getTranslationKey())) {
                key.setActive(false);
                changed.add(key);
                counter.keysRetired++;
            }
        }
        keyRepository.saveAll(changed);
        return existing;
    }

    private void syncLanguage(String language, Map<String, TranslationKey> keys, Map<String, Map<String, Translation>> byLanguage,
                              Map<String, String> englishHashes, Instant now, List<Translation> changed, Counter counter) {
        Map<String, Translation> rows = byLanguage.computeIfAbsent(language, ignored -> new HashMap<>());
        boolean english = InterfaceCatalog.SOURCE_LANGUAGE.equals(language);
        catalog.values(language).forEach((name, value) -> {
            Translation row = rows.get(name);
            String hash = english ? SourceHash.of(value) : englishHashes.get(name);
            if (row == null) {
                row = Translation.builder()
                        .translationKey(keys.get(name))
                        .languageCode(language)
                        .translationValue(value)
                        .source(TranslationSource.SEED)
                        .sourceHash(hash)
                        .updatedAt(now)
                        .build();
                rows.put(name, row);
                changed.add(row);
                counter.valuesInserted++;
                return;
            }
            TranslationSource source = row.getSource();
            if (source == null) {
                if (value.equals(row.getTranslationValue())) {
                    mark(row, TranslationSource.SEED, hash, now, changed);
                } else {
                    // Differs from the catalog and predates provenance: most likely edited by an administrator.
                    mark(row, TranslationSource.HUMAN, english ? SourceHash.of(row.getTranslationValue()) : hash, now, changed);
                    counter.legacyKeptAsHuman++;
                }
            } else if (source == TranslationSource.MACHINE) {
                row.setTranslationValue(value);
                mark(row, TranslationSource.SEED, hash, now, changed);
                counter.machineReplaced++;
            } else if (source == TranslationSource.SEED) {
                boolean valueChanged = !value.equals(row.getTranslationValue());
                if (valueChanged || !Objects.equals(hash, row.getSourceHash())) {
                    row.setTranslationValue(value);
                    mark(row, TranslationSource.SEED, hash, now, changed);
                    if (valueChanged) {
                        counter.valuesUpdated++;
                    }
                }
            }
            // HUMAN: never touched.
        });
    }

    private static void mark(Translation row, TranslationSource source, String hash, Instant now, List<Translation> changed) {
        row.setSource(source);
        row.setSourceHash(hash);
        row.setUpdatedAt(now);
        changed.add(row);
    }

    public record Result(int keysAdded, int keysReactivated, int keysRetired, int valuesInserted, int valuesUpdated,
                         int machineReplaced, int legacyKeptAsHuman, int legacyOutsideCatalog) {}

    private static final class Counter {
        int keysAdded;
        int keysReactivated;
        int keysRetired;
        int valuesInserted;
        int valuesUpdated;
        int machineReplaced;
        int legacyKeptAsHuman;
        int legacyOutsideCatalog;

        Result result() {
            return new Result(keysAdded, keysReactivated, keysRetired, valuesInserted, valuesUpdated, machineReplaced,
                    legacyKeptAsHuman, legacyOutsideCatalog);
        }
    }
}
