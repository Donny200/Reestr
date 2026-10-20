package adliya.uz.functioncatalogservice.service;

import java.util.regex.Pattern;

/** Normalization and plausibility checks shared by anonymous visitor texts (reports and help questions). */
public final class VisitorText {

    private static final Pattern LINE_BREAKS = Pattern.compile("\\r\\n?");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[\\h\\x0B\\f]+");
    private static final Pattern SPACE_AROUND_BREAKS = Pattern.compile(" ?\\n ?");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");
    private static final Pattern LINK = Pattern.compile("(?i)(https?://|www\\.)");
    /** 14+ digits, optionally separated by spaces or hyphens: personal identification, card or document numbers. */
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d(?:[ \\-]?\\d){13,}");

    /** Links one visitor text may contain. */
    public static final int MAX_LINKS = 3;

    private VisitorText() {}

    /** Unix line breaks, no control characters, single spaces, at most one blank line in a row, trimmed. */
    public static String normalize(String raw) {
        String value = LINE_BREAKS.matcher(raw == null ? "" : raw).replaceAll("\n");
        value = HORIZONTAL_SPACE.matcher(CONTROL_CHARACTERS.matcher(value).replaceAll(" ")).replaceAll(" ");
        return EXCESS_BLANK_LINES.matcher(SPACE_AROUND_BREAKS.matcher(value).replaceAll("\n")).replaceAll("\n\n").strip();
    }

    public static boolean meaningful(String normalized, int minLength) {
        return normalized.length() >= minLength && LETTER_OR_DIGIT.matcher(normalized).find();
    }

    public static long links(String normalized) {
        return LINK.matcher(normalized).results().count();
    }

    public static boolean containsLongNumber(String normalized) {
        return LONG_NUMBER.matcher(normalized).find();
    }
}
