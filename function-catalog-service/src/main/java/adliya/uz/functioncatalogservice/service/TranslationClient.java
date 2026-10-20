package adliya.uz.functioncatalogservice.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TranslationClient {
    Map<String, String> translate(String text, String fromCode, List<String> toCodes);
    default boolean isAvailable() { return false; }
    default Map<String, String> translateRequired(String text, String fromCode, List<String> toCodes) {
        return translate(text, fromCode, toCodes);
    }
    /**
     * Translates several texts into the same languages; one map per input text, in order. Fails like translateRequired.
     * Targets the provider cannot translate are left out of the maps; they never make the other targets fail.
     */
    default List<Map<String, String>> translateBatch(List<String> texts, String fromCode, List<String> toCodes) {
        return texts.stream().map(text -> translateRequired(text, fromCode, toCodes)).toList();
    }
    /** The given target codes the provider cannot produce from {@code fromCode}; all of them when the source is unsupported. */
    default Set<String> unsupportedTargets(String fromCode, Collection<String> toCodes) { return Set.of(); }
    /** True when the provider cannot translate from this language at all. */
    default boolean sourceUnsupported(String fromCode) { return false; }
}
