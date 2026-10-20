package adliya.uz.task1.service;

import adliya.uz.task1.dto.AddLanguageRequest;
import adliya.uz.task1.dto.LanguageCatalogItem;
import adliya.uz.task1.dto.LanguageSearchResult;
import adliya.uz.task1.entity.Language;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LanguageService {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NATIVE_NAME_LENGTH = 100;
    private static final List<CatalogEntry> CATALOG = Arrays.stream(Locale.getAvailableLocales())
            .filter(locale -> StringUtils.hasText(locale.getLanguage()))
            .filter(locale -> !"und".equalsIgnoreCase(locale.toLanguageTag()))
            .collect(Collectors.toMap(Locale::toLanguageTag, CatalogEntry::of, (first, ignored) -> first))
            .values().stream()
            .toList();

    private final LanguageRepository languageRepository;

    public List<LanguageCatalogItem> getCatalog() {
        return CATALOG.stream()
                .map(entry -> new LanguageCatalogItem(entry.code(), entry.nativeName()))
                .sorted(Comparator.comparing(LanguageCatalogItem::nameNative, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Language> getActive() {
        return languageRepository.findAllByActiveTrue();
    }

    public List<LanguageSearchResult> search(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        Set<String> addedCodes = languageRepository.findAll().stream()
                .map(language -> language.getCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return CATALOG.stream()
                .filter(entry -> entry.matches(needle))
                .map(entry -> new LanguageSearchResult(entry.code(), entry.englishName(), entry.nativeName(),
                        addedCodes.contains(entry.code().toLowerCase(Locale.ROOT))))
                .sorted(Comparator.comparing(LanguageSearchResult::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public Language add(AddLanguageRequest request) {
        Locale locale = parseLocale(request.code());
        String code = locale.toLanguageTag();
        if (languageRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalStateException("Language already enabled: " + code);
        }
        String nativeName = StringUtils.hasText(request.nativeName())
                ? request.nativeName().trim()
                : nativeName(locale);
        return languageRepository.save(Language.builder()
                .code(code)
                .nameNative(nativeName)
                .isDefault(false)
                .active(true)
                .build());
    }

    @Transactional
    public void remove(Long id) {
        Language language = languageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Language not found, ID: " + id));
        if (language.isDefault()) {
            throw new IllegalStateException("The default language cannot be removed: " + language.getCode());
        }
        languageRepository.delete(language);
    }

    private static Locale parseLocale(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            throw new IllegalArgumentException("Language code is required");
        }
        try {
            Locale locale = new Locale.Builder()
                    .setLanguageTag(rawCode.trim().replace('_', '-'))
                    .build();
            if (!StringUtils.hasText(locale.getLanguage()) || "und".equalsIgnoreCase(locale.toLanguageTag())) {
                throw new IllegalArgumentException("Invalid BCP 47 language tag: " + rawCode);
            }
            if (locale.toLanguageTag().length() > MAX_CODE_LENGTH) {
                throw new IllegalArgumentException("Language code must be at most 64 characters: " + rawCode);
            }
            return locale;
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid BCP 47 language tag: " + rawCode, exception);
        }
    }

    private static String englishName(Locale locale) {
        String value = locale.getDisplayName(Locale.ENGLISH);
        return StringUtils.hasText(value) ? value : locale.toLanguageTag();
    }

    private static String nativeName(Locale locale) {
        String value = locale.getDisplayName(locale);
        if (!StringUtils.hasText(value) || value.length() > MAX_NATIVE_NAME_LENGTH) {
            value = locale.getDisplayLanguage(locale);
        }
        return StringUtils.hasText(value) && value.length() <= MAX_NATIVE_NAME_LENGTH
                ? value
                : locale.toLanguageTag();
    }

    private record CatalogEntry(String code, String englishName, String nativeName, String searchText) {

        static CatalogEntry of(Locale locale) {
            String code = locale.toLanguageTag();
            String english = LanguageService.englishName(locale);
            String natural = LanguageService.nativeName(locale);
            return new CatalogEntry(code, english, natural,
                    (code + '\n' + english + '\n' + natural).toLowerCase(Locale.ROOT));
        }

        boolean matches(String needle) {
            return searchText.contains(needle);
        }
    }
}
