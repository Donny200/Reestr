package adliya.uz.functioncatalogservice.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ActiveLanguageClient {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient client;
    private volatile Snapshot snapshot;

    public ActiveLanguageClient(@Qualifier("identityRestClientBuilder") RestClient.Builder builder) {
        client = builder.clone().baseUrl("http://identity-service").build();
    }

    public Optional<Set<String>> activeLanguageCodes() {
        Snapshot current = snapshot;
        Instant now = Instant.now();
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(now)) {
            return Optional.of(current.codes());
        }
        try {
            Language[] languages = client.get().uri("/api/languages").retrieve().body(Language[].class);
            Set<String> codes = languages == null ? Set.of() : Arrays.stream(languages)
                    .map(Language::code)
                    .filter(Objects::nonNull)
                    .map(code -> code.trim().toLowerCase(Locale.ROOT))
                    .filter(code -> !code.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
            if (codes.isEmpty()) {
                return Optional.ofNullable(current).map(Snapshot::codes);
            }
            snapshot = new Snapshot(now, codes);
            return Optional.of(codes);
        } catch (RestClientException | IllegalStateException exception) {
            // Transport failure or no registered identity-service instance: fall back to the cached list, if any.
            return Optional.ofNullable(current).map(Snapshot::codes);
        }
    }

    private record Snapshot(Instant fetchedAt, Set<String> codes) {}

    private record Language(String code) {}
}
