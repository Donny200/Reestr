package adliya.uz.functioncatalogservice.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Interface dictionary of reference-service (the same labels the SPA shows) for documents generated on the server, such
 * as exports and service guides. Each language is cached for a few minutes; when reference-service is unreachable the
 * last known dictionary or the English fallbacks written next to each key are used, so a download never fails because
 * of missing labels.
 */
@Component
public class InterfaceLabels {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHED_LANGUAGES = 50;
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[a-z0-9]{2,8})*");

    private final RestClient client;
    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    public InterfaceLabels(@Qualifier("identityRestClientBuilder") RestClient.Builder builder) {
        client = builder.clone().baseUrl("http://reference-service").build();
    }

    /** Lower-case BCP 47-like code, or null when absent or malformed (the caller then uses fallbacks only). */
    public static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        return value.length() <= 35 && LANGUAGE.matcher(value).matches() ? value : null;
    }

    public Labels forLanguage(String language) {
        String code = normalizeLanguage(language);
        if (code == null) {
            return new Labels("en", Map.of());
        }
        Snapshot current = cache.get(code);
        Instant now = Instant.now();
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(now)) {
            return new Labels(code, current.values());
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> values = client.get().uri("/api/interface-translations/{code}", code).retrieve().body(Map.class);
            Map<String, String> copy = values == null ? Map.of() : Map.copyOf(values);
            if (cache.size() >= MAX_CACHED_LANGUAGES && !cache.containsKey(code)) {
                cache.clear();
            }
            cache.put(code, new Snapshot(now, copy));
            return new Labels(code, copy);
        } catch (RestClientException | IllegalStateException | ClassCastException exception) {
            // Transport failure or no registered reference-service instance: keep serving the last dictionary, if any.
            return new Labels(code, current == null ? Map.of() : current.values());
        }
    }

    public record Labels(String language, Map<String, String> values) {

        public String get(String key, String fallback) {
            String value = values.get(key);
            return value == null || value.isBlank() ? fallback : value;
        }

        public Locale locale() {
            return Locale.forLanguageTag(language);
        }
    }

    private record Snapshot(Instant fetchedAt, Map<String, String> values) {}
}
