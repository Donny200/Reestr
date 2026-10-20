package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

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
            TranslatedText translated = translations.get(language.toLowerCase(Locale.ROOT));
            if (translated != null && translated.text() != null && !translated.text().isBlank()) {
                return translated.text().strip();
            }
        }
        return original;
    }

    /** Localized value of one instruction field, or null when the card leaves it empty. */
    public static Instruction instruction(OrgFunction function, InstructionField field, String language) {
        String source = field.get(function);
        if (source == null || source.isBlank()) {
            return null;
        }
        String code = language == null ? null : language.toLowerCase(Locale.ROOT);
        if (code == null || code.equalsIgnoreCase(function.getSourceLanguage())) {
            return new Instruction(source.strip(), false, false);
        }
        TranslatedText translated = function.instructionTranslationsOf(field).get(code);
        if (translated != null && translated.text() != null && !translated.text().isBlank()) {
            return new Instruction(translated.text().strip(), TranslatedText.MACHINE.equals(translated.source()), false);
        }
        return new Instruction(source.strip(), false, true);
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
