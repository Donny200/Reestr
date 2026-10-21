package adliya.uz.functioncatalogservice.translation;

import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps the canonical BCP 47 tags the application stores (e.g. {@code fr-FR}, {@code pt-BR}, {@code uz-Cyrl}) to the
 * language codes Azure Translator accepts (e.g. {@code fr}, {@code pt}, none). The application keeps its own tag
 * everywhere; only the provider request uses the mapped code.
 *
 * <p>Rules, in order: explicit cases where Azure's code differs from BCP 47 (Chinese and Serbian scripts, Mongolian,
 * European vs Brazilian Portuguese, Canadian French, Kurdish, aliases such as {@code tl}/{@code no}); then the
 * provider's own {@code language-Script} or {@code language-REGION} code when it exists; then the bare language,
 * but only when the tag names no script or the script Azure uses for that language. A tag in another script (for
 * example {@code uz-Cyrl}, {@code az-Cyrl}, {@code pa-Arab}) is unsupported rather than translated into the wrong
 * script. A language whose default script is unknown here is only matched when the tag has no script.
 *
 * <p>Keep this class identical in reference-service and function-catalog-service (only the package differs).
 */
public final class AzureLanguageCodes {

    /**
     * Translation languages listed by {@code GET /languages?api-version=3.0&scope=translation}; used when the live list
     * cannot be fetched. The live list, when available, is authoritative.
     */
    public static final Set<String> KNOWN_PROVIDER_CODES = Set.of(
            "af", "am", "ar", "as", "az", "ba", "bg", "bho", "bn", "bo", "brx", "bs", "ca", "cs", "cy", "da", "de",
            "doi", "dsb", "dv", "el", "en", "es", "et", "eu", "fa", "fi", "fil", "fj", "fo", "fr", "fr-CA", "ga", "gl",
            "gom", "gu", "ha", "he", "hi", "hne", "hr", "hsb", "ht", "hu", "hy", "id", "ig", "ikt", "is", "it", "iu",
            "iu-Latn", "ja", "ka", "kk", "km", "kmr", "kn", "ko", "ks", "ku", "ky", "lb", "ln", "lo", "lt", "lug",
            "lv", "lzh", "mai", "mg", "mi", "mk", "ml", "mn-Cyrl", "mn-Mong", "mni", "mr", "ms", "mt", "mww", "my",
            "nb", "ne", "nl", "nso", "nya", "or", "otq", "pa", "pl", "prs", "ps", "pt", "pt-PT", "ro", "ru", "run",
            "rw", "sd", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr-Cyrl", "sr-Latn", "st", "sv", "sw", "ta", "te",
            "th", "ti", "tk", "tlh-Latn", "tlh-Piqd", "tn", "to", "tr", "tt", "ty", "ug", "uk", "ur", "uz", "vi",
            "xh", "yo", "yua", "yue", "zh-Hans", "zh-Hant", "zu");

    /** Script Azure produces for a language code without a script suffix (CLDR likely subtags). */
    private static final Map<String, String> PROVIDER_SCRIPT = Map.ofEntries(
            Map.entry("af", "Latn"), Map.entry("am", "Ethi"), Map.entry("ar", "Arab"), Map.entry("as", "Beng"),
            Map.entry("az", "Latn"), Map.entry("ba", "Cyrl"), Map.entry("bg", "Cyrl"), Map.entry("bho", "Deva"),
            Map.entry("bn", "Beng"), Map.entry("bo", "Tibt"), Map.entry("brx", "Deva"), Map.entry("bs", "Latn"),
            Map.entry("ca", "Latn"), Map.entry("cs", "Latn"), Map.entry("cy", "Latn"), Map.entry("da", "Latn"),
            Map.entry("de", "Latn"), Map.entry("doi", "Deva"), Map.entry("dsb", "Latn"), Map.entry("dv", "Thaa"),
            Map.entry("el", "Grek"), Map.entry("en", "Latn"), Map.entry("es", "Latn"), Map.entry("et", "Latn"),
            Map.entry("eu", "Latn"), Map.entry("fa", "Arab"), Map.entry("fi", "Latn"), Map.entry("fil", "Latn"),
            Map.entry("fj", "Latn"), Map.entry("fo", "Latn"), Map.entry("fr", "Latn"), Map.entry("ga", "Latn"),
            Map.entry("gl", "Latn"), Map.entry("gom", "Deva"), Map.entry("gu", "Gujr"), Map.entry("ha", "Latn"),
            Map.entry("he", "Hebr"), Map.entry("hi", "Deva"), Map.entry("hne", "Deva"), Map.entry("hr", "Latn"),
            Map.entry("hsb", "Latn"), Map.entry("ht", "Latn"), Map.entry("hu", "Latn"), Map.entry("hy", "Armn"),
            Map.entry("id", "Latn"), Map.entry("ig", "Latn"), Map.entry("ikt", "Latn"), Map.entry("is", "Latn"),
            Map.entry("it", "Latn"), Map.entry("iu", "Cans"), Map.entry("ja", "Jpan"), Map.entry("ka", "Geor"),
            Map.entry("kk", "Cyrl"), Map.entry("km", "Khmr"), Map.entry("kmr", "Latn"), Map.entry("kn", "Knda"),
            Map.entry("ko", "Kore"), Map.entry("ks", "Arab"), Map.entry("ku", "Arab"), Map.entry("ky", "Cyrl"),
            Map.entry("lb", "Latn"), Map.entry("ln", "Latn"), Map.entry("lo", "Laoo"), Map.entry("lt", "Latn"),
            Map.entry("lug", "Latn"), Map.entry("lv", "Latn"), Map.entry("lzh", "Hant"), Map.entry("mai", "Deva"),
            Map.entry("mg", "Latn"), Map.entry("mi", "Latn"), Map.entry("mk", "Cyrl"), Map.entry("ml", "Mlym"),
            Map.entry("mni", "Beng"), Map.entry("mr", "Deva"), Map.entry("ms", "Latn"), Map.entry("mt", "Latn"),
            Map.entry("mww", "Latn"), Map.entry("my", "Mymr"), Map.entry("nb", "Latn"), Map.entry("ne", "Deva"),
            Map.entry("nl", "Latn"), Map.entry("nso", "Latn"), Map.entry("nya", "Latn"), Map.entry("or", "Orya"),
            Map.entry("otq", "Latn"), Map.entry("pa", "Guru"), Map.entry("pl", "Latn"), Map.entry("prs", "Arab"),
            Map.entry("ps", "Arab"), Map.entry("pt", "Latn"), Map.entry("ro", "Latn"), Map.entry("ru", "Cyrl"),
            Map.entry("run", "Latn"), Map.entry("rw", "Latn"), Map.entry("sd", "Arab"), Map.entry("si", "Sinh"),
            Map.entry("sk", "Latn"), Map.entry("sl", "Latn"), Map.entry("sm", "Latn"), Map.entry("sn", "Latn"),
            Map.entry("so", "Latn"), Map.entry("sq", "Latn"), Map.entry("st", "Latn"), Map.entry("sv", "Latn"),
            Map.entry("sw", "Latn"), Map.entry("ta", "Taml"), Map.entry("te", "Telu"), Map.entry("th", "Thai"),
            Map.entry("ti", "Ethi"), Map.entry("tk", "Latn"), Map.entry("tn", "Latn"), Map.entry("to", "Latn"),
            Map.entry("tr", "Latn"), Map.entry("tt", "Cyrl"), Map.entry("ty", "Latn"), Map.entry("ug", "Arab"),
            Map.entry("uk", "Cyrl"), Map.entry("ur", "Arab"), Map.entry("uz", "Latn"), Map.entry("vi", "Latn"),
            Map.entry("xh", "Latn"), Map.entry("yo", "Latn"), Map.entry("yua", "Latn"), Map.entry("yue", "Hant"),
            Map.entry("zu", "Latn"));

    /** BCP 47 language subtags whose Azure code is a different subtag. */
    private static final Map<String, String> LANGUAGE_ALIASES = Map.of(
            "tl", "fil",   // Tagalog: Azure offers Filipino
            "no", "nb",    // Norwegian macrolanguage: Azure offers Bokmål
            "iw", "he", "in", "id", "ji", "yi",
            "ckb", "ku",   // Central Kurdish (Sorani) is "ku" at Azure
            "ku", "kmr");  // BCP 47 "ku" defaults to Kurmanji (Latin), which is "kmr" at Azure

    private static final Set<String> TRADITIONAL_CHINESE_REGIONS = Set.of("TW", "HK", "MO");
    private static final Set<String> BRAZILIAN_PORTUGUESE_REGIONS = Set.of("", "BR");

    private AzureLanguageCodes() {}

    /**
     * Azure code for the application tag, if the provider can translate into (or from) it.
     *
     * @param tag       canonical or lower-case BCP 47 tag as stored by the application
     * @param supported provider codes currently offered (live list or {@link #KNOWN_PROVIDER_CODES})
     */
    public static Optional<String> providerCode(String tag, Set<String> supported) {
        Locale locale = parse(tag);
        if (locale == null) {
            return Optional.empty();
        }
        Map<String, String> byLowerCase = supported.stream()
                .collect(Collectors.toMap(code -> code.toLowerCase(Locale.ROOT), code -> code, (first, ignored) -> first));
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String script = locale.getScript();
        String region = locale.getCountry().toUpperCase(Locale.ROOT);
        String providerLanguage = LANGUAGE_ALIASES.getOrDefault(language, language);

        for (String candidate : candidates(language, providerLanguage, script, region)) {
            String match = byLowerCase.get(candidate.toLowerCase(Locale.ROOT));
            if (match != null) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    /** Canonical form of a stored tag ({@code fr-fr} becomes {@code fr-FR}); null when it is not a valid tag. */
    public static String canonical(String tag) {
        Locale locale = parse(tag);
        return locale == null ? null : locale.toLanguageTag();
    }

    private static Set<String> candidates(String language, String providerLanguage, String script, String region) {
        Set<String> result = new LinkedHashSet<>();
        switch (language) {
            case "zh" -> {
                boolean traditional = "Hant".equals(script) || (script.isEmpty() && TRADITIONAL_CHINESE_REGIONS.contains(region));
                if (script.isEmpty() || "Hans".equals(script) || "Hant".equals(script)) {
                    result.add(traditional ? "zh-Hant" : "zh-Hans");
                }
                return result;
            }
            case "sr" -> {
                if ("Latn".equals(script)) {
                    result.add("sr-Latn");
                } else if (script.isEmpty() || "Cyrl".equals(script)) {
                    result.add("sr-Cyrl");
                }
                return result;
            }
            case "mn" -> {
                if ("Mong".equals(script)) {
                    result.add("mn-Mong");
                } else if (script.isEmpty() || "Cyrl".equals(script)) {
                    result.add("mn-Cyrl");
                }
                return result;
            }
            case "pt" -> {
                if (script.isEmpty() || "Latn".equals(script)) {
                    result.add(BRAZILIAN_PORTUGUESE_REGIONS.contains(region) ? "pt" : "pt-PT");
                }
                return result;
            }
            default -> {
                // fall through to the generic rules
            }
        }
        if (!script.isEmpty()) {
            result.add(providerLanguage + "-" + script);
        }
        if (!region.isEmpty()) {
            result.add(providerLanguage + "-" + region);
        }
        if (script.isEmpty() || script.equals(PROVIDER_SCRIPT.get(providerLanguage))) {
            result.add(providerLanguage);
        }
        return result;
    }

    private static Locale parse(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        try {
            Locale locale = new Locale.Builder().setLanguageTag(tag.trim().replace('_', '-')).build();
            return locale.getLanguage().isEmpty() ? null : locale;
        } catch (IllformedLocaleException exception) {
            return null;
        }
    }
}
