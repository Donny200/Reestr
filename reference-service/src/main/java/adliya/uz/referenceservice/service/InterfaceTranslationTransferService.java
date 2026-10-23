package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.catalog.SourceHash;
import adliya.uz.referenceservice.config.CacheConfig;
import adliya.uz.referenceservice.dto.InterfaceTranslationExport;
import adliya.uz.referenceservice.dto.InterfaceTranslationExport.Entry;
import adliya.uz.referenceservice.dto.InterfaceTranslationExport.ImportResult;
import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.entity.TranslationSource;
import adliya.uz.referenceservice.repository.TranslationKeyRepository;
import adliya.uz.referenceservice.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JSON backup and restore of the interface translations in the database, which is their source of truth. The files
 * are not read at start-up: the shipped catalog (src/main/resources/i18n/interface) stays the only seed, and a restore
 * is an explicit administrator action.
 *
 * <p>Restoring never loses a person's work: a value edited by a person in this installation is kept unless the caller
 * asks to replace such values with the file's human values; curated (catalog) values are only replaced by human values;
 * a machine value is only replaced by a human or curated one, or by a machine value written for the current English
 * text when the stored one is outdated.</p>
 */
@Service
@RequiredArgsConstructor
public class InterfaceTranslationTransferService {

    private static final int MAX_ENTRIES = 20_000;
    private static final int MAX_REPORTED_KEYS = 50;

    private final TranslationRepository translationRepository;
    private final TranslationKeyRepository keyRepository;
    private final LanguageTagNormalizer normalizer;

    @Transactional(readOnly = true)
    public InterfaceTranslationExport export(String rawLanguageCode, Instant now) {
        String code = normalizer.normalize(rawLanguageCode);
        Map<String, String> englishHashes = englishHashes();
        List<Entry> entries = translationRepository.findDictionary(code).stream()
                .map(row -> new Entry(row.getTranslationKey().getTranslationKey(), row.getTranslationValue(), row.getSource(),
                        row.getSourceHash(), isCurrent(code, row, englishHashes), row.getTranslationKey().isActive(),
                        row.getUpdatedAt()))
                .toList();
        return new InterfaceTranslationExport(InterfaceTranslationExport.FORMAT, code,
                InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE, now, entries);
    }

    /** Every language that has stored translations, active or not. */
    @Transactional(readOnly = true)
    public InterfaceTranslationExport.Bundle exportAll(Instant now) {
        List<InterfaceTranslationExport> languages = translationRepository.findLanguageCodes().stream()
                .sorted()
                .map(code -> export(code, now))
                .toList();
        return new InterfaceTranslationExport.Bundle(InterfaceTranslationExport.FORMAT, now, languages);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DICTIONARY_CACHE, allEntries = true)
    public ImportResult importLanguage(String rawLanguageCode, InterfaceTranslationExport document, boolean replaceHuman,
                                       Instant now) {
        String code = normalizer.normalize(rawLanguageCode);
        if (document == null || !InterfaceTranslationExport.FORMAT.equals(document.format())) {
            throw new IllegalArgumentException("Not an interface translation export (format must be "
                    + InterfaceTranslationExport.FORMAT + ")");
        }
        if (document.languageCode() != null && !code.equals(normalizer.normalize(document.languageCode()))) {
            throw new IllegalArgumentException("The file holds translations for " + document.languageCode()
                    + ", not for " + code);
        }
        List<Entry> entries = document.entries() == null ? List.of() : document.entries();
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("At most " + MAX_ENTRIES + " entries can be imported at once");
        }

        Map<String, TranslationKey> keys = keyRepository.findAll().stream()
                .collect(Collectors.toMap(TranslationKey::getTranslationKey, Function.identity()));
        Map<String, Translation> existing = translationRepository.findDictionary(code).stream()
                .collect(Collectors.toMap(row -> row.getTranslationKey().getTranslationKey(), Function.identity()));
        Map<String, String> englishHashes = englishHashes();

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int keptHuman = 0;
        int skipped = 0;
        List<String> unknown = new ArrayList<>();
        Map<String, Translation> changed = new LinkedHashMap<>();
        for (Entry entry : entries) {
            TranslationKey key = entry == null || entry.key() == null ? null : keys.get(entry.key().trim());
            if (key == null) {
                skipped++;
                if (entry != null && entry.key() != null && unknown.size() < MAX_REPORTED_KEYS) {
                    unknown.add(entry.key());
                }
                continue;
            }
            if (entry.value() == null || entry.value().isBlank()) {
                skipped++;
                continue;
            }
            String value = entry.value().trim();
            TranslationSource source = entry.source() == null ? TranslationSource.HUMAN : entry.source();
            Translation row = changed.containsKey(key.getTranslationKey()) ? changed.get(key.getTranslationKey())
                    : existing.get(key.getTranslationKey());
            if (row == null) {
                row = Translation.builder().translationKey(key).languageCode(code).build();
                created++;
            } else if (row.getTranslationValue().equals(value)) {
                unchanged++;
                continue;
            } else {
                switch (decide(row, source, entry.sourceHash(), englishHashes.get(key.getTranslationKey()), replaceHuman)) {
                    case KEEP_HUMAN -> {
                        keptHuman++;
                        continue;
                    }
                    case SKIP -> {
                        skipped++;
                        continue;
                    }
                    case REPLACE -> updated++;
                }
            }
            row.setTranslationValue(value);
            row.setSource(source);
            row.setSourceHash(entry.sourceHash());
            row.setUpdatedAt(entry.updatedAt() == null ? now : entry.updatedAt());
            changed.put(key.getTranslationKey(), row);
        }
        translationRepository.saveAll(changed.values());
        return new ImportResult(code, created, updated, unchanged, keptHuman, skipped, unknown);
    }

    private enum Decision { REPLACE, KEEP_HUMAN, SKIP }

    private static Decision decide(Translation stored, TranslationSource incoming, String incomingHash, String englishHash,
                                   boolean replaceHuman) {
        TranslationSource current = stored.getSource();
        if (current == null || current == TranslationSource.HUMAN) {
            return incoming == TranslationSource.HUMAN && replaceHuman ? Decision.REPLACE : Decision.KEEP_HUMAN;
        }
        if (current == TranslationSource.SEED) {
            return incoming == TranslationSource.HUMAN ? Decision.REPLACE : Decision.SKIP;
        }
        // Stored machine value: people and the catalog win; another machine value only when it is the fresher one.
        if (incoming != TranslationSource.MACHINE) {
            return Decision.REPLACE;
        }
        boolean storedCurrent = Objects.equals(stored.getSourceHash(), englishHash);
        boolean incomingCurrent = Objects.equals(incomingHash, englishHash);
        return incomingCurrent && !storedCurrent ? Decision.REPLACE : Decision.SKIP;
    }

    private Map<String, String> englishHashes() {
        Map<String, String> hashes = new HashMap<>();
        for (Translation row : translationRepository.findDictionary(InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE)) {
            hashes.put(row.getTranslationKey().getTranslationKey(), SourceHash.of(row.getTranslationValue()));
        }
        return hashes;
    }

    private static boolean isCurrent(String code, Translation row, Map<String, String> englishHashes) {
        if (InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE.equals(code) || row.getSourceHash() == null) {
            return true;
        }
        return row.getSourceHash().equals(englishHashes.get(row.getTranslationKey().getTranslationKey()));
    }
}
