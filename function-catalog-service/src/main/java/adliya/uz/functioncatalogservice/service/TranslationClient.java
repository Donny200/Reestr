package adliya.uz.functioncatalogservice.service;

import java.util.List;
import java.util.Map;

public interface TranslationClient {
    Map<String, String> translate(String text, String fromCode, List<String> toCodes);
    default boolean isAvailable() { return false; }
    default Map<String, String> translateRequired(String text, String fromCode, List<String> toCodes) {
        return translate(text, fromCode, toCodes);
    }
    /** Translates several texts into the same languages; one map per input text, in order. Fails like translateRequired. */
    default List<Map<String, String>> translateBatch(List<String> texts, String fromCode, List<String> toCodes) {
        return texts.stream().map(text -> translateRequired(text, fromCode, toCodes)).toList();
    }
}
