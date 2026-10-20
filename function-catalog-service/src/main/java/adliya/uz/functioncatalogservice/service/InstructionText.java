package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.InstructionField;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class InstructionText {

    private static final Pattern LINE_BREAKS = Pattern.compile("\\r\\n?");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

    private InstructionText() {}

    public static String normalize(InstructionField field, String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = CONTROL_CHARACTERS.matcher(LINE_BREAKS.matcher(raw).replaceAll("\n")).replaceAll("");
        String value = field.list() ? normalizeList(field, cleaned) : EXCESS_BLANK_LINES.matcher(cleaned.strip()).replaceAll("\n\n");
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > field.maxLength()) {
            throw new IllegalArgumentException(field.key() + ": at most " + field.maxLength() + " characters");
        }
        return value;
    }

    /** Items of a normalized list field (one per line); empty for null. */
    public static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\n")).map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private static String normalizeList(InstructionField field, String value) {
        List<String> items = Arrays.stream(value.split("\n")).map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (items.size() > InstructionField.MAX_LIST_ITEMS) {
            throw new IllegalArgumentException(field.key() + ": at most " + InstructionField.MAX_LIST_ITEMS + " items");
        }
        return String.join("\n", items);
    }
}
