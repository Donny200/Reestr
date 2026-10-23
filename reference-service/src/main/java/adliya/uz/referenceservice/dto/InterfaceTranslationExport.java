package adliya.uz.referenceservice.dto;

import adliya.uz.referenceservice.entity.TranslationSource;

import java.time.Instant;
import java.util.List;

/**
 * Backup of the interface translations stored for one language tag (its own rows, not the values it inherits), in the
 * format {@code POST /api/interface-translations/{lang}/import} restores. Provenance travels with each value, so a
 * restore keeps machine values recognisable (and re-translatable when the English text has changed since).
 *
 * @param format         {@link #FORMAT}; other values are refused on import
 * @param sourceLanguage the language the source hashes refer to (always {@code en})
 */
public record InterfaceTranslationExport(
        String format,
        String languageCode,
        String sourceLanguage,
        Instant exportedAt,
        List<Entry> entries
) {
    public static final String FORMAT = "reestr-interface-translations/1";

    /**
     * @param source     who wrote the value; missing in a hand-written file, which is then imported as a human edit
     * @param sourceHash SHA-256 of the English text the value was written for, if known
     * @param current    on export: whether the value was written for the current English text (informational)
     * @param active     on export: whether the key is still used by the interface (informational)
     */
    public record Entry(String key, String value, TranslationSource source, String sourceHash, Boolean current,
                        Boolean active, Instant updatedAt) {}

    /** Several languages in one file ({@code GET /api/interface-translations/export}). */
    public record Bundle(String format, Instant exportedAt, List<InterfaceTranslationExport> languages) {}

    /**
     * Outcome of an import.
     *
     * @param keptHuman   values left as they are because a person edited them in this installation
     * @param skipped     entries not imported: unknown keys, empty values, curated values the catalog owns, or machine
     *                    values older than the stored ones
     * @param unknownKeys up to 50 keys this installation does not define
     */
    public record ImportResult(String languageCode, int created, int updated, int unchanged, int keptHuman, int skipped,
                               List<String> unknownKeys) {}
}
