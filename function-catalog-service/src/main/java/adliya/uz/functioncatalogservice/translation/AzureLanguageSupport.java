package adliya.uz.functioncatalogservice.translation;

import adliya.uz.functioncatalogservice.config.AzureTranslatorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which languages Azure Translator currently offers. The public {@code /languages} list (no subscription key is sent)
 * is cached for a day; when it cannot be fetched, the build-time list {@link AzureLanguageCodes#KNOWN_PROVIDER_CODES}
 * is used and the fetch is retried after a few minutes. Codes the provider rejected at request time are remembered
 * until the next successful refresh.
 */
@Component
@Slf4j
public class AzureLanguageSupport {

    static final Duration TTL = Duration.ofHours(24);
    static final Duration FAILURE_TTL = Duration.ofMinutes(5);
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 5000;

    private final AzureTranslatorProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final Set<String> rejected = ConcurrentHashMap.newKeySet();
    private volatile Snapshot snapshot;

    @Autowired
    public AzureLanguageSupport(AzureTranslatorProperties properties) {
        this(properties, createRestClient(), Clock.systemUTC());
    }

    public AzureLanguageSupport(AzureTranslatorProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    /** Support based only on the build-time list; never calls the provider (tests, unconfigured clients). */
    public static AzureLanguageSupport offline() {
        return new AzureLanguageSupport(new AzureTranslatorProperties(), null, Clock.systemUTC());
    }

    /** Provider code for an application tag, or empty when Azure cannot translate into or from it. */
    public Optional<String> providerCode(String applicationTag) {
        return AzureLanguageCodes.providerCode(applicationTag, providerCodes())
                .filter(code -> !rejected.contains(code.toLowerCase(Locale.ROOT)));
    }

    public boolean supports(String applicationTag) {
        return providerCode(applicationTag).isPresent();
    }

    /** Remembers a provider code Azure refused although it was listed (its list changed since the last refresh). */
    public void markRejected(String providerCode) {
        if (StringUtils.hasText(providerCode)) {
            rejected.add(providerCode.toLowerCase(Locale.ROOT));
            log.warn("Azure Translator rejected language code {}; treated as unsupported until the language list refreshes", providerCode);
        }
    }

    public Set<String> providerCodes() {
        Instant now = clock.instant();
        Snapshot current = snapshot;
        if (current != null && current.validUntil().isAfter(now)) {
            return current.codes();
        }
        if (restClient == null || !StringUtils.hasText(properties.getEndpoint())) {
            return AzureLanguageCodes.KNOWN_PROVIDER_CODES;
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && current.validUntil().isAfter(now)) {
                return current.codes();
            }
            Set<String> live = fetch();
            if (live.isEmpty()) {
                Set<String> fallback = current == null ? AzureLanguageCodes.KNOWN_PROVIDER_CODES : current.codes();
                snapshot = new Snapshot(fallback, now.plus(FAILURE_TTL));
                return fallback;
            }
            rejected.clear();
            snapshot = new Snapshot(live, now.plus(TTL));
            return live;
        }
    }

    private Set<String> fetch() {
        try {
            JsonNode body = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(properties.getEndpoint())
                            .path("/languages")
                            .queryParam("api-version", "3.0")
                            .queryParam("scope", "translation")
                            .build(true).toUri())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode translation = body == null ? null : body.get("translation");
            if (translation == null || !translation.isObject()) {
                return Set.of();
            }
            Set<String> codes = new LinkedHashSet<>();
            for (Iterator<String> names = translation.fieldNames(); names.hasNext(); ) {
                codes.add(names.next());
            }
            return Set.copyOf(codes);
        } catch (RuntimeException exception) {
            log.warn("Azure Translator language list unavailable ({}); using the built-in list", exception.getClass().getSimpleName());
            return Set.of();
        }
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return RestClient.builder().requestFactory(factory).build();
    }

    private record Snapshot(Set<String> codes, Instant validUntil) {}
}
