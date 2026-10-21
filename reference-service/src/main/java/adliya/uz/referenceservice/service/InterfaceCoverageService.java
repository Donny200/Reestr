package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.catalog.SourceHash;
import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.entity.TranslationSource;
import adliya.uz.referenceservice.repository.TranslationKeyRepository;
import adliya.uz.referenceservice.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a visitor of each language actually sees: for every required, active key, which row the dictionary serves
 * (the most specific level of the language hierarchy that has one, see {@link LanguageHierarchy#sameScript}), who
 * wrote it, and whether it was written for the current English text. Languages whose base is English are fully covered
 * by the source dictionary.
 */
@Service
@RequiredArgsConstructor
public class InterfaceCoverageService {

    private final TranslationKeyRepository keyRepository;
    private final TranslationRepository translationRepository;

    @Transactional(readOnly = true)
    public Map<String, LanguageCoverage> coverage(Collection<String> languageCodes) {
        Snapshot snapshot = snapshot(languageCodes);
        Map<String, LanguageCoverage> result = new LinkedHashMap<>();
        for (String code : languageCodes) {
            result.put(code, snapshot.coverage(code));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public LanguageCoverage coverage(String languageCode) {
        return snapshot(List.of(languageCode)).coverage(languageCode);
    }

    private Snapshot snapshot(Collection<String> languageCodes) {
        Set<String> needed = new LinkedHashSet<>();
        needed.add(InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE);
        languageCodes.forEach(code -> needed.addAll(LanguageHierarchy.sameScript(code)));
        Map<String, Map<String, Translation>> rows = new HashMap<>();
        for (Translation translation : translationRepository.findAllByLanguages(needed)) {
            rows.computeIfAbsent(translation.getLanguageCode(), ignored -> new HashMap<>())
                    .put(translation.getTranslationKey().getTranslationKey(), translation);
        }
        List<TranslationKey> required = keyRepository.findAllByActiveTrueOrderByTranslationKeyAsc().stream()
                .filter(TranslationKey::isRequired)
                .toList();
        return new Snapshot(required, rows);
    }

    /**
     * @param missingKeys        required keys with no value at any level of the hierarchy (shown in English)
     * @param staleMachineKeys   machine values of this exact language written for an older English text
     * @param outdatedHumanKeys  human values written for an older English text (kept, flagged for review)
     * @param englishByKey       current English text of every required key (source for machine translation)
     */
    public record LanguageCoverage(String languageCode, boolean englishBase, long requiredKeys, long translatedKeys,
                                   long seedKeys, long humanKeys, long machineKeys, List<String> missingKeys,
                                   List<String> staleMachineKeys, List<String> outdatedHumanKeys,
                                   Map<String, String> englishByKey) {

        public double percentage() {
            return requiredKeys == 0 ? 100.0 : translatedKeys * 100.0 / requiredKeys;
        }

        /** Keys a machine translation job for this language should (re)write. */
        public List<String> keysToTranslate() {
            List<String> keys = new ArrayList<>(missingKeys);
            keys.addAll(staleMachineKeys);
            return keys;
        }
    }

    private record Snapshot(List<TranslationKey> required, Map<String, Map<String, Translation>> rows) {

        LanguageCoverage coverage(String code) {
            List<String> hierarchy = LanguageHierarchy.sameScript(code);
            String source = InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE;
            boolean englishBase = source.equals(hierarchy.get(0));
            Map<String, Translation> english = rows.getOrDefault(source, Map.of());
            Map<String, String> englishByKey = new LinkedHashMap<>();
            long translated = 0;
            long seed = 0;
            long human = 0;
            long machine = 0;
            List<String> missing = new ArrayList<>();
            List<String> stale = new ArrayList<>();
            List<String> outdated = new ArrayList<>();
            for (TranslationKey key : required) {
                String name = key.getTranslationKey();
                Translation englishRow = english.get(name);
                String englishText = englishRow == null ? null : englishRow.getTranslationValue();
                if (englishText != null) {
                    englishByKey.put(name, englishText);
                }
                Translation served = englishBase ? englishRow : served(name, hierarchy);
                if (served == null) {
                    missing.add(name);
                    continue;
                }
                translated++;
                TranslationSource origin = served.getSource();
                String currentHash = englishText == null ? null : SourceHash.of(englishText);
                boolean current = englishBase || currentHash == null || currentHash.equals(served.getSourceHash());
                if (origin == TranslationSource.MACHINE) {
                    machine++;
                    if (!current && code.equals(served.getLanguageCode())) {
                        stale.add(name);
                    }
                } else if (origin == TranslationSource.HUMAN) {
                    human++;
                    if (!current && served.getSourceHash() != null) {
                        outdated.add(name);
                    }
                } else {
                    seed++;
                }
            }
            return new LanguageCoverage(code, englishBase, required.size(), translated, seed, human, machine,
                    missing, stale, outdated, englishByKey);
        }

        private Translation served(String key, List<String> hierarchy) {
            for (int i = hierarchy.size() - 1; i >= 0; i--) {
                String level = hierarchy.get(i);
                if (InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE.equals(level)) {
                    continue;
                }
                Translation row = rows.getOrDefault(level, Map.of()).get(key);
                if (row != null) {
                    return row;
                }
            }
            return null;
        }
    }
}
