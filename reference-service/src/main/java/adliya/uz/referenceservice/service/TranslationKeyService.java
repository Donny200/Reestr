package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.catalog.SourceHash;
import adliya.uz.referenceservice.config.CacheConfig;
import adliya.uz.referenceservice.dto.CreateTranslationKeyRequest;
import adliya.uz.referenceservice.dto.TranslationKeyResponse;
import adliya.uz.referenceservice.dto.UpdateTranslationKeyRequest;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TranslationKeyService {

    private final TranslationKeyRepository translationKeyRepository;
    private final TranslationRepository translationRepository;

    @Transactional(readOnly = true)
    public List<TranslationKeyResponse> getAll() {
        Map<String, String> english = translationRepository
                .findDictionary(InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getTranslationKey().getTranslationKey(),
                        Translation::getTranslationValue
                ));
        return translationKeyRepository.findAll().stream()
                .sorted(Comparator.comparing(TranslationKey::getTranslationKey))
                .map(key -> TranslationKeyResponse.from(key, english.get(key.getTranslationKey())))
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DICTIONARY_CACHE, allEntries = true)
    public TranslationKeyResponse create(CreateTranslationKeyRequest request) {
        String keyName = TranslationKeys.normalize(request.translationKey());
        if (translationKeyRepository.findByTranslationKey(keyName).isPresent()) {
            throw new IllegalStateException("Translation key already exists: " + keyName);
        }

        TranslationKey key = translationKeyRepository.save(TranslationKey.builder()
                .translationKey(keyName)
                .description(normalizeDescription(request.description()))
                .required(request.required() == null || request.required())
                .active(true)
                .build());
        String defaultValue = normalizeDefaultValue(request.defaultValue());
        translationRepository.save(Translation.builder()
                .translationKey(key)
                .languageCode(InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE)
                .translationValue(defaultValue)
                .source(TranslationSource.HUMAN)
                .sourceHash(SourceHash.of(defaultValue))
                .updatedAt(Instant.now())
                .build());
        return TranslationKeyResponse.from(key, defaultValue);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DICTIONARY_CACHE, allEntries = true)
    public TranslationKeyResponse update(Long id, UpdateTranslationKeyRequest request) {
        TranslationKey key = requireKey(id);
        if (request.description() != null) {
            key.setDescription(normalizeDescription(request.description()));
        }
        if (request.required() != null) {
            key.setRequired(request.required());
        }
        if (request.active() != null) {
            key.setActive(request.active());
        }
        TranslationKey saved = translationKeyRepository.save(key);
        String defaultValue = translationRepository
                .findByLanguageCodeAndTranslationKey_TranslationKey(
                        InterfaceTranslationDictionaryService.SOURCE_LANGUAGE_CODE,
                        saved.getTranslationKey()
                )
                .map(Translation::getTranslationValue)
                .orElse(null);
        return TranslationKeyResponse.from(saved, defaultValue);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DICTIONARY_CACHE, allEntries = true)
    public void deactivate(Long id) {
        TranslationKey key = requireKey(id);
        key.setActive(false);
        translationKeyRepository.save(key);
    }

    private TranslationKey requireKey(Long id) {
        return translationKeyRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Translation key not found, ID: " + id));
    }

    private String normalizeDefaultValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Default English value must not be blank");
        }
        return value.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
