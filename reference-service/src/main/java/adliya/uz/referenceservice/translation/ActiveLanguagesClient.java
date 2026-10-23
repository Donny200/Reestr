package adliya.uz.referenceservice.translation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Active interface languages from identity-service ({@code GET /api/languages}), canonical BCP 47, cached briefly. */
@Component
public class ActiveLanguagesClient {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient client;
    private volatile Snapshot snapshot;

    public ActiveLanguagesClient(@Qualifier("serviceRestClientBuilder") RestClient.Builder builder) {
        client = builder.clone().baseUrl("http://identity-service").build();
    }

    /** For the background scan: a list at most five minutes old. */
    public Optional<Set<String>> activeLanguageCodes() {
        Snapshot current = snapshot;
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return Optional.of(current.codes());
        }
        return fetch(current);
    }

    /**
     * For the admin console: asks identity-service now, so a language added or removed a moment ago is shown (and can
     * be queued) at once. Falls back to the last known list when identity-service cannot be reached.
     */
    public Optional<Set<String>> currentActiveLanguageCodes() {
        return fetch(snapshot);
    }

    private Optional<Set<String>> fetch(Snapshot current) {
        Instant now = Instant.now();
        try {
            Language[] languages = client.get().uri("/api/languages").retrieve().body(Language[].class);
            Set<String> codes = new LinkedHashSet<>();
            if (languages != null) {
                Arrays.stream(languages).map(Language::code).filter(Objects::nonNull)
                        .map(AzureLanguageCodes::canonical).filter(Objects::nonNull).forEach(codes::add);
            }
            if (codes.isEmpty()) {
                return Optional.ofNullable(current).map(Snapshot::codes);
            }
            snapshot = new Snapshot(now, Set.copyOf(codes));
            return Optional.of(snapshot.codes());
        } catch (RestClientException | IllegalStateException exception) {
            // Transport failure or no registered identity-service instance: keep the last known list, if any.
            return Optional.ofNullable(current).map(Snapshot::codes);
        }
    }

    private record Snapshot(Instant fetchedAt, Set<String> codes) {}

    private record Language(String code) {}
}
