package adliya.uz.functioncatalogservice.entity;

import java.util.LinkedHashMap;
import java.util.Map;

public record TranslatedText(String text, String source) {

    public static final String HUMAN = "human";
    public static final String MACHINE = "machine";

    public static Map<String, TranslatedText> humanEdits(Map<String, String> values) {
        Map<String, TranslatedText> result = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((code, text) -> result.put(code, new TranslatedText(text, HUMAN)));
        }
        return result;
    }
}
