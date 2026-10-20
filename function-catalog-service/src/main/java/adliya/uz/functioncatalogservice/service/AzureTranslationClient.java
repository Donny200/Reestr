package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AzureTranslatorProperties;
import adliya.uz.functioncatalogservice.exception.TranslationUnavailableException;
import adliya.uz.functioncatalogservice.translation.AzureLanguageSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Azure Translator v3. Application tags (stored lower-case, e.g. {@code fr-fr}, {@code uz-cyrl}) are mapped to Azure
 * codes with {@link adliya.uz.functioncatalogservice.translation.AzureLanguageCodes}; only supported codes are
 * requested, each once, and the answers are copied back to every application tag that maps to them. Unsupported
 * targets are left out of the result and never make the supported ones fail.
 */
@Component
@Slf4j
public class AzureTranslationClient implements TranslationClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 8000;
    private static final int MAX_BATCH_TEXTS = 25;
    /** Azure error code for an invalid source language ("from"). */
    private static final int INVALID_SOURCE = 400035;
    /** Azure error codes for an unsupported or invalid target language. */
    private static final Set<Integer> INVALID_TARGET = Set.of(400019, 400023, 400036);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AzureTranslatorProperties properties;
    private final RestClient restClient;
    private final AzureLanguageSupport support;

    @Autowired
    public AzureTranslationClient(AzureTranslatorProperties properties, AzureLanguageSupport support) {
        this(properties, createRestClient(), support);
    }

    public AzureTranslationClient(AzureTranslatorProperties properties) {
        this(properties, createRestClient(), AzureLanguageSupport.offline());
    }

    AzureTranslationClient(AzureTranslatorProperties properties, RestClient restClient) {
        this(properties, restClient, AzureLanguageSupport.offline());
    }

    AzureTranslationClient(AzureTranslatorProperties properties, RestClient restClient, AzureLanguageSupport support) {
        this.properties = properties;
        this.restClient = restClient;
        this.support = support;
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
            return emptyMaps(texts.size());
        }
        if (!isAvailable()) {
            throw new TranslationUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "Automatic translation is not configured");
        }
        Optional<String> source = support.providerCode(fromCode);
        if (source.isEmpty()) {
            log.info("Azure Translator cannot translate from {}; nothing requested", fromCode);
            return emptyMaps(texts.size());
        }
        Map<String, List<String>> byProviderCode = new LinkedHashMap<>();
        for (String target : targets) {
            support.providerCode(target)
                    .filter(code -> !code.equalsIgnoreCase(source.get()))
                    .ifPresent(code -> byProviderCode.computeIfAbsent(code, ignored -> new ArrayList<>()).add(target));
        }
        if (byProviderCode.isEmpty()) {
            return emptyMaps(texts.size());
        }
        List<String> providerCodes = List.copyOf(byProviderCode.keySet());
        List<Map<String, String>> result = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += MAX_BATCH_TEXTS) {
            List<String> chunk = texts.subList(from, Math.min(texts.size(), from + MAX_BATCH_TEXTS));
            for (Map<String, String> byProvider : requestIsolating(chunk, source.get(), providerCodes)) {
                result.add(fanOut(byProvider, byProviderCode));
            }
        }
        return result;
    }

    @Override
    public Set<String> unsupportedTargets(String fromCode, Collection<String> toCodes) {
        if (toCodes == null || toCodes.isEmpty()) {
            return Set.of();
        }
        boolean sourceSupported = StringUtils.hasText(fromCode) && support.supports(fromCode);
        Set<String> result = new LinkedHashSet<>();
        for (String code : toCodes) {
            if (StringUtils.hasText(code) && !code.trim().equalsIgnoreCase(fromCode)
                    && (!sourceSupported || !support.supports(code.trim()))) {
                result.add(code.trim());
            }
        }
        return result;
    }

    @Override
    public boolean sourceUnsupported(String fromCode) {
        return StringUtils.hasText(fromCode) && !support.supports(fromCode);
    }

    /**
     * A 400 for a multi-language request is retried language by language, so one code Azure no longer accepts does
     * not block the others; that code is then treated as unsupported.
     */
    private List<Map<String, String>> requestIsolating(List<String> texts, String source, List<String> providerCodes) {
        try {
            return request(texts, source, providerCodes);
        } catch (LanguageRejected rejected) {
            if (rejected.source()) {
                support.markRejected(source);
                return emptyMaps(texts.size());
            }
            if (providerCodes.size() == 1) {
                return rejectSingle(rejected, providerCodes.get(0), texts.size());
            }
            List<Map<String, String>> merged = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                merged.add(new LinkedHashMap<>());
            }
            for (String code : providerCodes) {
                List<Map<String, String>> single;
                try {
                    single = request(texts, source, List.of(code));
                } catch (LanguageRejected alone) {
                    single = rejectSingle(alone, code, texts.size());
                }
                for (int i = 0; i < texts.size(); i++) {
                    merged.get(i).putAll(single.get(i));
                }
            }
            return merged;
        }
    }

    private List<Map<String, String>> rejectSingle(LanguageRejected rejected, String code, int size) {
        if (!rejected.languageError()) {
            throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY,
                    "Translator rejected the request; retry later or contact the administrator");
        }
        support.markRejected(code);
        return emptyMaps(size);
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
            if (status == HttpStatus.BAD_REQUEST.value()) {
                Integer code = errorCode(exception);
                throw new LanguageRejected(code != null && code == INVALID_SOURCE, code != null && INVALID_TARGET.contains(code));
            }
            throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY,
                    "Translator rejected the request; retry later or contact the administrator");
        } catch (Exception exception) {
            log.warn("Azure Translator request failed ({})", exception.getClass().getSimpleName());
            throw new TranslationUnavailableException(HttpStatus.BAD_GATEWAY, "Automatic translation failed; retry later");
        }
    }

    /** Azure's numeric error code from the response body; the body itself is never logged. */
    private static Integer errorCode(RestClientResponseException exception) {
        try {
            JsonNode code = JSON.readTree(exception.getResponseBodyAsString()).path("error").path("code");
            return code.isInt() ? code.asInt() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> targetCodes(String fromCode, List<String> toCodes) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String code : toCodes) {
            if (StringUtils.hasText(code) && !code.trim().equalsIgnoreCase(fromCode.trim())) {
                unique.putIfAbsent(code.trim().toLowerCase(Locale.ROOT), code.trim());
            }
        }
        return List.copyOf(unique.values());
    }

    private static Map<String, String> fanOut(Map<String, String> byProvider, Map<String, List<String>> byProviderCode) {
        Map<String, String> result = new LinkedHashMap<>();
        byProviderCode.forEach((providerCode, applicationCodes) -> {
            String text = byProvider.get(providerCode.toLowerCase(Locale.ROOT));
            if (text != null) {
                applicationCodes.forEach(code -> result.put(code, text));
            }
        });
        return result;
    }

    private static List<Map<String, String>> emptyMaps(int size) {
        List<Map<String, String>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(Map.of());
        }
        return result;
    }

    private URI translateUri(String fromCode, List<String> targets) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getEndpoint())
                .path("/translate")
                .queryParam("api-version", "3.0")
                .queryParam("from", fromCode);
        targets.forEach(code -> builder.queryParam("to", code));
        return builder.build(true).toUri();
    }

    /** Provider answers keyed by the lower-cased provider code. */
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

    /** HTTP 400 from Azure: {@code source} when the source language was refused, {@code languageError} for a target. */
    private static final class LanguageRejected extends RuntimeException {
        private final boolean source;
        private final boolean languageError;

        LanguageRejected(boolean source, boolean languageError) {
            super(null, null, false, false);
            this.source = source;
            this.languageError = languageError;
        }

        boolean source() { return source; }

        boolean languageError() { return languageError; }
    }

    private record AzureResponse(List<AzureTranslation> translations) {}

    private record AzureTranslation(String text, String to) {}
}
