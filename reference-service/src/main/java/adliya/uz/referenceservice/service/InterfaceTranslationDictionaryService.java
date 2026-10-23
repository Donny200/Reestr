package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.config.CacheConfig;
import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterfaceTranslationDictionaryService {

    public static final String SOURCE_LANGUAGE_CODE = "en";

    private final TranslationRepository translationRepository;

    @Cacheable(cacheNames = CacheConfig.DICTIONARY_CACHE, key = "#languageCode", sync = true)
    @Transactional(readOnly = true)
    public Map<String, String> getTranslations(String languageCode) {
        Map<String, String> result = dictionaryForCode(SOURCE_LANGUAGE_CODE);
        // Same script only: uz-Cyrl never shows Latin Uzbek; what the language lacks is shown in English.
        for (String candidate : LanguageHierarchy.sameScript(languageCode)) {
            if (!SOURCE_LANGUAGE_CODE.equals(candidate)) {
                result.putAll(dictionaryForCode(candidate));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getExactTranslations(String languageCode) {
        return Collections.unmodifiableMap(dictionaryForCode(languageCode));
    }

    private Map<String, String> dictionaryForCode(String languageCode) {
        Map<String, String> dictionary = new LinkedHashMap<>();
        for (Translation translation : translationRepository.findActiveDictionary(languageCode)) {
            dictionary.put(
                    translation.getTranslationKey().getTranslationKey(),
                    translation.getTranslationValue()
            );
        }
        return dictionary;
    }
}
