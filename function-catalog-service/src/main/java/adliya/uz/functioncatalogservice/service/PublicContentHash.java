package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fingerprints of the public sections of a card. Two publications are compared section by section through these
 * hashes, so the history can name what changed without storing any previous text.
 */
public final class PublicContentHash {

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String DOCUMENTS = "documents";
    public static final String STEPS = "steps";
    public static final String WHO_CAN_USE = "whoCanUse";
    public static final String WHERE_HOW_TO_APPLY = "whereHowToApply";
    public static final String PROCESSING_TIME = "processingTime";
    public static final String FEE = "fee";
    public static final String SOURCE_LINK = "sourceLink";
    public static final String ORGANIZATION = "organization";
    public static final String CATEGORY = "category";
    public static final String TRANSLATIONS = "translations";

    /** Display order of sections in the public history. */
    public static final List<String> SECTIONS = List.of(NAME, DESCRIPTION, DOCUMENTS, STEPS, WHO_CAN_USE, WHERE_HOW_TO_APPLY,
            PROCESSING_TIME, FEE, SOURCE_LINK, ORGANIZATION, CATEGORY, TRANSLATIONS);

    private PublicContentHash() {}

    public static Map<String, String> of(OrgFunction function) {
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put(NAME, hash(function.getName()));
        hashes.put(DESCRIPTION, hash(function.getDescription()));
        hashes.put(DOCUMENTS, hash(join(function.getRequiredDocuments(), function.getRequirements()) + details(function)));
        hashes.put(STEPS, hash(function.getSteps()));
        hashes.put(WHO_CAN_USE, hash(function.getWhoCanUse()));
        hashes.put(WHERE_HOW_TO_APPLY, hash(function.getWhereHowToApply()));
        hashes.put(PROCESSING_TIME, hash(function.getProcessingTime()));
        hashes.put(FEE, hash(function.getFee()));
        hashes.put(SOURCE_LINK, hash(function.getOfficialSourceUrl()));
        hashes.put(ORGANIZATION, hash(String.valueOf(function.getOrganizationId())));
        hashes.put(CATEGORY, hash(function.getFunctionCategory() == null ? null : String.valueOf(function.getFunctionCategory().getId())));
        hashes.put(TRANSLATIONS, hash(translations(function)));
        return hashes;
    }

    /** Sections whose fingerprint differs; an unknown previous fingerprint counts as unchanged (nothing to compare). */
    public static List<String> changedSections(Map<String, String> previous, Map<String, String> current) {
        if (previous == null) {
            return List.of();
        }
        return SECTIONS.stream()
                .filter(section -> previous.containsKey(section) && !previous.get(section).equals(current.get(section)))
                .toList();
    }

    private static String translations(OrgFunction function) {
        String source = function.getSourceLanguage() == null ? "" : function.getSourceLanguage().toLowerCase(Locale.ROOT);
        StringBuilder text = new StringBuilder();
        append(text, "name", function.getNameTranslations(), source);
        append(text, "description", function.getDescriptionTranslations(), source);
        for (InstructionField field : InstructionField.values()) {
            append(text, field.key(), function.instructionTranslationsOf(field), source);
        }
        return text.toString();
    }

    private static void append(StringBuilder text, String field, Map<String, TranslatedText> values, String sourceLanguage) {
        if (values == null) {
            return;
        }
        new TreeMap<>(values).forEach((language, value) -> {
            if (!language.equalsIgnoreCase(sourceLanguage) && value != null && value.text() != null && !value.text().isBlank()) {
                text.append(field).append('[').append(language).append("]=").append(value.text().strip()).append('\n');
            }
        });
    }

    /** Structured document details; empty for cards without any, so their fingerprint is unchanged. */
    private static String details(OrgFunction function) {
        StringBuilder text = new StringBuilder();
        for (DocumentDetail detail : function.documentDetailsOrEmpty()) {
            text.append("\n#").append(normalize(detail.document())).append('|').append(detail.form()).append('|').append(detail.copies())
                    .append('|').append(normalize(detail.validity())).append('|').append(normalize(detail.notes()))
                    .append('|').append(normalize(detail.formUrl()));
        }
        return text.toString();
    }

    private static String join(String first, String second) {
        return normalize(first) + "\n---\n" + normalize(second);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String hash(String value) {
        return Sha256.hex(normalize(value).getBytes(StandardCharsets.UTF_8));
    }
}
