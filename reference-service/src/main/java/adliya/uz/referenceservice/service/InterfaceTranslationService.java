package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.config.CacheConfig;
import adliya.uz.referenceservice.dto.TranslationCoverageResponse;
import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.repository.TranslationKeyRepository;
import adliya.uz.referenceservice.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterfaceTranslationService {

    private final TranslationKeyRepository translationKeyRepository;
    private final TranslationRepository translationRepository;
    private final InterfaceTranslationDictionaryService dictionaryService;
    private final LanguageTagNormalizer languageTagNormalizer;

    public Map<String, String> getTranslations(String languageCode) {
        return dictionaryService.getTranslations(languageTagNormalizer.normalize(languageCode));
    }

    public Map<String, String> getExactTranslations(String languageCode) {
        return dictionaryService.getExactTranslations(languageTagNormalizer.normalize(languageCode));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DICTIONARY_CACHE, allEntries = true)
    public void updateTranslations(String languageCode, Map<String, String> translations) {
        String code = languageTagNormalizer.normalize(languageCode);
        if (translations == null || translations.isEmpty()) {
            throw new IllegalArgumentException("Translations must not be empty");
        }

        Map<String, String> normalizedValues = normalizeValues(translations);
        Map<String, TranslationKey> keysByName = translationKeyRepository
                .findAllByTranslationKeyInAndActiveTrue(normalizedValues.keySet())
                .stream()
                .collect(Collectors.toMap(TranslationKey::getTranslationKey, Function.identity()));

        Set<String> unknownKeys = new LinkedHashSet<>(normalizedValues.keySet());
        unknownKeys.removeAll(keysByName.keySet());
        if (!unknownKeys.isEmpty()) {
            throw new IllegalArgumentException("Unknown or inactive translation keys: " + String.join(", ", unknownKeys));
        }

        List<Long> keyIds = keysByName.values().stream().map(TranslationKey::getId).toList();
        Map<Long, Translation> existingByKeyId = translationRepository
                .findAllByLanguageAndKeyIds(code, keyIds)
                .stream()
                .collect(Collectors.toMap(item -> item.getTranslationKey().getId(), Function.identity()));

        List<Translation> changed = new ArrayList<>(normalizedValues.size());
        normalizedValues.forEach((keyName, value) -> {
            TranslationKey key = keysByName.get(keyName);
            Translation translation = existingByKeyId.get(key.getId());
            if (translation == null) {
                translation = Translation.builder()
                        .translationKey(key)
                        .languageCode(code)
                        .translationValue(value)
                        .build();
            } else {
                translation.setTranslationValue(value);
            }
            changed.add(translation);
        });
        translationRepository.saveAll(changed);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DICTIONARY_CACHE, allEntries = true)
    public void deleteTranslation(String languageCode, String key) {
        String code = languageTagNormalizer.normalize(languageCode);
        String normalizedKey = TranslationKeys.normalize(key);
        translationRepository.findByLanguageCodeAndTranslationKey_TranslationKey(code, normalizedKey)
                .ifPresent(translationRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<String> getMissingKeys(String languageCode) {
        String code = languageTagNormalizer.normalize(languageCode);
        Set<String> translatedKeys = dictionaryService.getExactTranslations(code).keySet();
        return translationKeyRepository.findAllByActiveTrueOrderByTranslationKeyAsc().stream()
                .map(TranslationKey::getTranslationKey)
                .filter(key -> !translatedKeys.contains(key))
                .toList();
    }

    @Transactional(readOnly = true)
    public TranslationCoverageResponse getCoverage(String languageCode) {
        String code = languageTagNormalizer.normalize(languageCode);
        Collection<TranslationKey> activeKeys = translationKeyRepository.findAllByActiveTrueOrderByTranslationKeyAsc();
        Set<String> translatedKeys = dictionaryService.getExactTranslations(code).keySet();
        long requiredKeys = activeKeys.stream().filter(TranslationKey::isRequired).count();
        long translatedRequiredKeys = activeKeys.stream()
                .filter(TranslationKey::isRequired)
                .map(TranslationKey::getTranslationKey)
                .filter(translatedKeys::contains)
                .count();
        long missingKeys = requiredKeys - translatedRequiredKeys;
        double percentage = requiredKeys == 0 ? 100.0 : translatedRequiredKeys * 100.0 / requiredKeys;
        return new TranslationCoverageResponse(
                code,
                requiredKeys,
                translatedRequiredKeys,
                missingKeys,
                percentage,
                missingKeys == 0
        );
    }

    private Map<String, String> normalizeValues(Map<String, String> translations) {
        Map<String, String> normalized = new LinkedHashMap<>();
        translations.forEach((key, value) -> {
            String normalizedKey = TranslationKeys.normalize(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Translation value must not be blank: " + normalizedKey);
            }
            normalized.put(normalizedKey, value.trim());
        });
        return normalized;
    }
}
