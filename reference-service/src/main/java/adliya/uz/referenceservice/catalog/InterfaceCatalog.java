package adliya.uz.referenceservice.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shipped interface dictionary: {@code classpath:i18n/interface/<language>.json}, one flat {@code key -> text}
 * object per curated language. {@code en.json} is the source of truth for which keys exist and for their English
 * text; the other files hold curated translations of those keys. The SPA, server-generated documents and the
 * coverage report all use these keys.
 *
 * <p>Counted texts are plural groups: {@code key_one} and {@code key_other} in English, plus whatever CLDR forms a
 * language needs ({@code key_few}, {@code key_many} in Russian). Those extra forms exist only in the language files
 * and are optional keys: a language without them falls back to {@code key_other}.
 */
@Component
public class InterfaceCatalog {

    public static final String SOURCE_LANGUAGE = "en";
    private static final String LOCATION = "classpath*:i18n/interface/*.json";

    private static final Pattern PLURAL_FORM = Pattern.compile("(.+)_(zero|one|two|few|many|other)");

    private final Map<String, Map<String, String>> byLanguage;
    private final Set<String> pluralForms;

    @Autowired
    public InterfaceCatalog(ObjectMapper objectMapper) {
        this(load(objectMapper));
    }

    InterfaceCatalog(Map<String, Map<String, String>> byLanguage) {
        Map<String, String> source = byLanguage.get(SOURCE_LANGUAGE);
        if (source == null || source.isEmpty()) {
            throw new IllegalStateException("Interface catalog has no " + SOURCE_LANGUAGE + ".json");
        }
        Map<String, Map<String, String>> copy = new TreeMap<>();
        Set<String> extraForms = new TreeSet<>();
        byLanguage.forEach((language, values) -> {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                Matcher plural = PLURAL_FORM.matcher(entry.getKey());
                if (!source.containsKey(entry.getKey()) && plural.matches() && source.containsKey(plural.group(1) + "_other")) {
                    extraForms.add(entry.getKey());
                } else if (!source.containsKey(entry.getKey())) {
                    throw new IllegalStateException("Interface catalog " + language + ".json has key missing from "
                            + SOURCE_LANGUAGE + ".json: " + entry.getKey());
                }
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    throw new IllegalStateException("Interface catalog " + language + ".json has an empty value: " + entry.getKey());
                }
            }
            copy.put(language, Collections.unmodifiableMap(new LinkedHashMap<>(values)));
        });
        this.byLanguage = Collections.unmodifiableMap(copy);
        this.pluralForms = Collections.unmodifiableSet(extraForms);
    }

    /** Required keys: every key of {@value #SOURCE_LANGUAGE}.json. */
    public Set<String> keys() {
        return byLanguage.get(SOURCE_LANGUAGE).keySet();
    }

    /** Language-specific plural forms (optional keys with no English text of their own). */
    public Set<String> pluralForms() {
        return pluralForms;
    }

    public Map<String, String> source() {
        return byLanguage.get(SOURCE_LANGUAGE);
    }

    /** Curated languages, including {@value #SOURCE_LANGUAGE}. */
    public Set<String> languages() {
        return byLanguage.keySet();
    }

    public Map<String, String> values(String language) {
        return byLanguage.getOrDefault(language, Map.of());
    }

    private static Map<String, Map<String, String>> load(ObjectMapper objectMapper) {
        try {
            Map<String, Map<String, String>> result = new TreeMap<>();
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(LOCATION)) {
                String name = resource.getFilename();
                if (name == null || !name.endsWith(".json")) {
                    continue;
                }
                try (InputStream input = resource.getInputStream()) {
                    result.put(name.substring(0, name.length() - ".json".length()),
                            objectMapper.readValue(input, new TypeReference<LinkedHashMap<String, String>>() {}));
                }
            }
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException("Interface catalog could not be read", exception);
        }
    }
}
