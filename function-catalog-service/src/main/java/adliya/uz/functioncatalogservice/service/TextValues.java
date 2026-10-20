package adliya.uz.functioncatalogservice.service;

import java.util.Locale;

/** Null-tolerant helpers for optional short texts such as notes, labels and language codes. */
final class TextValues {

    private TextValues() {}

    /** Null when missing or blank, otherwise stripped of leading and trailing white space. */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** A lower-case language code, or null when none was given. */
    static String language(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
