package adliya.uz.referenceservice.translation;

import adliya.uz.referenceservice.config.AzureTranslatorProperties;
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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Languages Azure Translator can currently translate, from its public language list (no subscription key is sent).
 * Cached for a day; when the list cannot be fetched the {@link AzureLanguageCodes#KNOWN_PROVIDER_CODES} snapshot is
 * used and the fetch is retried after a short pause.
 */
@Component
@Slf4j
public class AzureLanguageSupport {

    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration FAILURE_TTL = Duration.ofMinutes(10);

    private final AzureTranslatorProperties properties;
    private final RestClient client;
    private final Clock clock;
    private volatile Snapshot snapshot;

    @Autowired
    public AzureLanguageSupport(AzureTranslatorProperties properties) {
        this(properties, defaultClient(), Clock.systemUTC());
    }

    AzureLanguageSupport(AzureTranslatorProperties properties, RestClient client, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.clock = clock;
    }

    public Set<String> providerCodes() {
        Instant now = clock.instant();
        Snapshot current = snapshot;
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.codes();
        }
        Snapshot next = fetch(now);
        snapshot = next;
        return next.codes();
    }

    /** Provider code for an application tag, if Azure can translate into it now. */
    public Optional<String> providerCode(String languageTag) {
        return AzureLanguageCodes.providerCode(languageTag, providerCodes());
    }

    private Snapshot fetch(Instant now) {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            return new Snapshot(AzureLanguageCodes.KNOWN_PROVIDER_CODES, now.plus(FAILURE_TTL));
        }
        try {
            JsonNode body = client.get()
                    .uri(UriComponentsBuilder.fromUriString(properties.getEndpoint()).path("/languages")
                            .queryParam("api-version", "3.0").queryParam("scope", "translation").build(true).toUri())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode translation = body == null ? null : body.get("translation");
            if (translation == null || !translation.isObject() || translation.isEmpty()) {
                throw new IllegalStateException("empty language list");
            }
            Set<String> codes = new LinkedHashSet<>();
            translation.fieldNames().forEachRemaining(codes::add);
            return new Snapshot(Set.copyOf(codes), now.plus(TTL));
        } catch (RuntimeException exception) {
            log.warn("Azure Translator language list unavailable ({}); using the built-in list", exception.getClass().getSimpleName());
            return new Snapshot(AzureLanguageCodes.KNOWN_PROVIDER_CODES, now.plus(FAILURE_TTL));
        }
    }

    private static RestClient defaultClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        return RestClient.builder().requestFactory(factory).build();
    }

    private record Snapshot(Set<String> codes, Instant expiresAt) {}
}
