package adliya.uz.referenceservice.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fallback chain of a canonical tag, most general first: {@code uz-Cyrl-UZ} gives {@code uz, uz-Cyrl, uz-Cyrl-UZ}.
 * The dictionary is the English base overlaid with each level in this order, so a variant only needs the entries that
 * differ from its parent language.
 */
public final class LanguageHierarchy {

    /** Usual script of languages commonly written in more than one (CLDR likely subtags). */
    private static final Map<String, String> DEFAULT_SCRIPTS = Map.ofEntries(
            Map.entry("az", "Latn"), Map.entry("bs", "Latn"), Map.entry("ha", "Latn"), Map.entry("iu", "Cans"),
            Map.entry("kk", "Cyrl"), Map.entry("ks", "Arab"), Map.entry("ku", "Latn"), Map.entry("ky", "Cyrl"),
            Map.entry("mn", "Cyrl"), Map.entry("ms", "Latn"), Map.entry("pa", "Guru"), Map.entry("sd", "Arab"),
            Map.entry("shi", "Tfng"), Map.entry("sr", "Cyrl"), Map.entry("tg", "Cyrl"), Map.entry("tk", "Latn"),
            Map.entry("ug", "Arab"), Map.entry("uz", "Latn"), Map.entry("vai", "Vaii"), Map.entry("yue", "Hant"),
            Map.entry("zh", "Hans"), Map.entry("en", "Latn"), Map.entry("fr", "Latn"), Map.entry("de", "Latn"),
            Map.entry("ru", "Cyrl"), Map.entry("tt", "Cyrl"), Map.entry("ba", "Cyrl"));

    private LanguageHierarchy() {}

    /**
     * The chain without ancestors written in another script: {@code uz-Cyrl} gives {@code uz-Cyrl} only, because
     * {@code uz} is Latin. Both the served dictionary and the coverage report use it, so a Cyrillic reader never gets
     * Latin text: until its own translation exists, a key is served from the English base (and counted as missing).
     */
    public static List<String> sameScript(String languageCode) {
        Locale locale = Locale.forLanguageTag(languageCode);
        String script = locale.getScript();
        String usual = DEFAULT_SCRIPTS.get(locale.getLanguage());
        List<String> chain = of(languageCode);
        if (script.isBlank() || usual == null || usual.equals(script)) {
            return chain;
        }
        String prefix = locale.getLanguage() + "-" + script;
        return chain.stream().filter(level -> level.equals(prefix) || level.startsWith(prefix + "-")).toList();
    }

    public static List<String> of(String languageCode) {
        Locale locale = Locale.forLanguageTag(languageCode);
        Set<String> hierarchy = new LinkedHashSet<>();
        hierarchy.add(locale.getLanguage());

        if (!locale.getScript().isBlank()) {
            hierarchy.add(new Locale.Builder()
                    .setLanguage(locale.getLanguage())
                    .setScript(locale.getScript())
                    .build()
                    .toLanguageTag());
        }

        if (!locale.getCountry().isBlank()) {
            Locale.Builder regional = new Locale.Builder()
                    .setLanguage(locale.getLanguage())
                    .setRegion(locale.getCountry());
            if (!locale.getScript().isBlank()) {
                regional.setScript(locale.getScript());
            }
            hierarchy.add(regional.build().toLanguageTag());
        }

        hierarchy.add(languageCode);
        return new ArrayList<>(hierarchy);
    }
}
