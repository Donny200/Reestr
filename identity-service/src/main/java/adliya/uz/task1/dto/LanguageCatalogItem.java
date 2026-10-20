package adliya.uz.task1.dto;

import java.util.Locale;

public record LanguageCatalogItem(String code, String nameNative) {

    public static LanguageCatalogItem fromCode(String code) {
        Locale locale = Locale.forLanguageTag(code);
        return new LanguageCatalogItem(code, locale.getDisplayLanguage(locale));
    }
}
