package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.translation.AzureLanguageCodes;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which active languages still lack a usable translation. The source language never counts as missing, and neither
 * does a regional variant written the same way (e.g. {@code fr-fr} or {@code en-gb} for an {@code fr}/{@code en}
 * source): readers of those tags are shown the source text itself.
 */
public final class MissingTranslations {

    public static final String DEFAULT_CATEGORY_LANGUAGE = "en";

    private MissingTranslations() {}

    public static List<String> targets(Set<String> activeLanguages, String sourceLanguage) {
        String source = normalize(sourceLanguage);
        return activeLanguages.stream()
                .map(MissingTranslations::normalize)
                .filter(code -> !code.isEmpty() && !code.equals(source) && !sameWrittenLanguage(code, source))
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> of(OrgFunction function, Set<String> activeLanguages) {
        return targets(activeLanguages, function.getSourceLanguage()).stream()
                .filter(code -> !translated(function, code))
                .toList();
    }

    public static List<String> of(FunctionCategory category, Set<String> activeLanguages) {
        return targets(activeLanguages, sourceLanguageOf(category)).stream()
                .filter(code -> !present(category.getNameTranslations(), code))
                .toList();
    }

    public static String sourceLanguageOf(FunctionCategory category) {
        return category.getSourceLanguage() == null || category.getSourceLanguage().isBlank()
                ? DEFAULT_CATEGORY_LANGUAGE
                : normalize(category.getSourceLanguage());
    }

    public static boolean translated(OrgFunction function, String code) {
        if (!present(function.getNameTranslations(), code)) {
            return false;
        }
        if (!isBlank(function.getDescription()) && !present(function.getDescriptionTranslations(), code)) {
            return false;
        }
        for (InstructionField field : InstructionField.values()) {
            if (!isBlank(field.get(function)) && !present(function.instructionTranslationsOf(field), code)) {
                return false;
            }
        }
        return true;
    }

    /** True when both tags map to the same provider language (same language and script, region aside). */
    public static boolean sameWrittenLanguage(String first, String second) {
        if (first == null || second == null || first.isBlank() || second.isBlank()) {
            return false;
        }
        return AzureLanguageCodes.providerCode(first, AzureLanguageCodes.KNOWN_PROVIDER_CODES)
                .flatMap(left -> AzureLanguageCodes.providerCode(second, AzureLanguageCodes.KNOWN_PROVIDER_CODES).map(left::equalsIgnoreCase))
                .orElse(false);
    }

    public static boolean present(Map<String, TranslatedText> translations, String code) {
        TranslatedText value = translations == null ? null : translations.get(code);
        return value != null && !isBlank(value.text());
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }
}
