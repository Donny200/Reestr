package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AzureTranslatorProperties;
import adliya.uz.functioncatalogservice.exception.TranslationUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class AzureTranslationClient implements TranslationClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 8000;
    private static final int MAX_BATCH_TEXTS = 25;

    private final AzureTranslatorProperties properties;
    private final RestClient restClient;

    @Autowired
    public AzureTranslationClient(AzureTranslatorProperties properties) {
        this(properties, createRestClient());
    }

    AzureTranslationClient(AzureTranslatorProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(properties.getEndpoint())
                && StringUtils.hasText(properties.getKey())
                && StringUtils.hasText(properties.getRegion());
    }

    @Override
    public Map<String, String> translate(String text, String fromCode, List<String> toCodes) {
        try {
            return translateRequired(text, fromCode, toCodes);
        } catch (TranslationUnavailableException exception) {
            log.warn("Azure Translator is unavailable ({}); translations remain pending", exception.status().value());
            return Map.of();
        }
    }

    @Override
    public Map<String, String> translateRequired(String text, String fromCode, List<String> toCodes) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        List<Map<String, String>> translated = translateBatch(List.of(text), fromCode, toCodes);
        return translated.isEmpty() ? Map.of() : translated.get(0);
    }

    /**
     * One request for up to {@value #MAX_BATCH_TEXTS} texts (Azure accepts 100 texts / 50 000 characters per call);
     * larger inputs are split. The response is matched to the inputs by position.
     */
    @Override
    public List<Map<String, String>> translateBatch(List<String> texts, String fromCode, List<String> toCodes) {
        if (texts == null || texts.isEmpty() || !StringUtils.hasText(fromCode)) {
            return List.of();
        }
        List<String> targets = targetCodes(fromCode, toCodes);
        if (targets.isEmpty()) {
            return texts.stream().<Map<String, String>>map(ignored -> Map.of()).toList();
        }
        if (!isAvailable()) {
            throw new TranslationUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "Automatic translation is not configured");
        }
        List<Map<String, String>> result = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += MAX_BATCH_TEXTS) {
            result.addAll(request(texts.subList(from, Math.min(texts.size(), from + MAX_BATCH_TEXTS)), fromCode, targets));
        }
        return result;
    }

    private List<Map<String, String>> request(List<String> texts, String fromCode, List<String> targets) {
        try {
            AzureResponse[] response = restClient.post()
                    .uri(translateUri(fromCode, targets))
                    .header("Ocp-Apim-Subscription-Key", properties.getKey())
                    .header("Ocp-Apim-Subscription-Region", properties.getRegion())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(texts.stream().map(text -> Map.of("Text", text)).toList())
                    .retrieve()
                    .body(AzureResponse[].class);
            return toTranslations(response, texts.size());
        } catch (TranslationUnavailableException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new TranslationUnavailableException(HttpStatus.GATEWAY_TIMEOUT, "Translator did not respond; retry later");
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("Azure Translator returned HTTP {}", status);
            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new TranslationUnavailableException(HttpStatus.TOO_MANY_REQUESTS, "Translator rate limit reached; retry later");
            }
            throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY,
                    "Translator rejected the request; retry later or contact the administrator");
        } catch (Exception exception) {
            log.warn("Azure Translator request failed ({})", exception.getClass().getSimpleName());
            throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY, "Automatic translation failed; retry later");
        }
    }

    private static List<String> targetCodes(String fromCode, List<String> toCodes) {
        return toCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(code -> code.toLowerCase(Locale.ROOT))
                .filter(code -> !code.equalsIgnoreCase(fromCode))
                .distinct()
                .toList();
    }

    private URI translateUri(String fromCode, List<String> targets) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getEndpoint())
                .path("/translate")
                .queryParam("api-version", "3.0")
                .queryParam("from", fromCode);
        targets.forEach(code -> builder.queryParam("to", code));
        return builder.build(true).toUri();
    }

    private static List<Map<String, String>> toTranslations(AzureResponse[] response, int expected) {
        if (response == null || response.length != expected) {
            throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY, "Translator returned no translations");
        }
        List<Map<String, String>> result = new ArrayList<>(expected);
        for (AzureResponse item : response) {
            if (item == null || item.translations() == null) {
                throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY, "Translator returned no translations");
            }
            Map<String, String> translated = new LinkedHashMap<>();
            for (AzureTranslation translation : item.translations()) {
                if (translation != null && StringUtils.hasText(translation.to()) && StringUtils.hasText(translation.text())) {
                    translated.put(translation.to().toLowerCase(Locale.ROOT), translation.text());
                }
            }
            result.add(translated);
        }
        return result;
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return RestClient.builder().requestFactory(factory).build();
    }

    private record AzureResponse(List<AzureTranslation> translations) {}

    private record AzureTranslation(String text, String to) {}
}
