package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Which active languages still lack a usable translation; the source language never counts as missing. */
public final class MissingTranslations {

    public static final String DEFAULT_CATEGORY_LANGUAGE = "en";

    private MissingTranslations() {}

    public static List<String> targets(Set<String> activeLanguages, String sourceLanguage) {
        String source = normalize(sourceLanguage);
        return activeLanguages.stream()
                .map(MissingTranslations::normalize)
                .filter(code -> !code.isEmpty() && !code.equals(source))
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
