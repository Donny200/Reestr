package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Server-side counterpart of the SPA's localizedText / localizedInstruction: translation when present, else the source. */
public final class LocalizedContent {

    private static final Pattern LIST_NUMBERING = Pattern.compile("^\\s*\\d+[.)]\\s*");

    private LocalizedContent() {}

    public static String text(String original, Map<String, TranslatedText> translations, String language) {
        if (language != null && translations != null) {
            for (String code : hierarchy(language)) {
                TranslatedText translated = translations.get(code);
                if (translated != null && translated.text() != null && !translated.text().isBlank()) {
                    return translated.text().strip();
                }
            }
        }
        return original;
    }

    /**
     * Localized value of one instruction field, or null when the card leaves it empty. A regional or script variant
     * falls back to its parent tag ({@code fr-fr} to {@code fr}) before the source text is used.
     */
    public static Instruction instruction(OrgFunction function, InstructionField field, String language) {
        String source = field.get(function);
        if (source == null || source.isBlank()) {
            return null;
        }
        if (language == null) {
            return new Instruction(source.strip(), false, false);
        }
        String sourceLanguage = function.getSourceLanguage() == null ? null : function.getSourceLanguage().toLowerCase(Locale.ROOT);
        Map<String, TranslatedText> translations = function.instructionTranslationsOf(field);
        for (String code : hierarchy(language)) {
            if (code.equals(sourceLanguage)) {
                return new Instruction(source.strip(), false, false);
            }
            TranslatedText translated = translations.get(code);
            if (translated != null && translated.text() != null && !translated.text().isBlank()) {
                return new Instruction(translated.text().strip(), TranslatedText.MACHINE.equals(translated.source()), false);
            }
        }
        return new Instruction(source.strip(), false, true);
    }

    /** The tag and its parents, most specific first: {@code uz-latn-uz}, {@code uz-latn}, {@code uz}. */
    static List<String> hierarchy(String language) {
        String code = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        while (!code.isEmpty()) {
            result.add(code);
            int dash = code.lastIndexOf('-');
            code = dash < 0 ? "" : code.substring(0, dash);
        }
        return result;
    }

    /** One list item per non-empty line, leading "1." / "2)" numbering removed (as the public page shows them). */
    public static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("\\r?\\n"))
                .map(line -> LIST_NUMBERING.matcher(line).replaceFirst("").strip())
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public record Instruction(String text, boolean machine, boolean fallback) {}
}
