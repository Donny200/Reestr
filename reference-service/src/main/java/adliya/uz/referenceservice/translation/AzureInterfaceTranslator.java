package adliya.uz.referenceservice.translation;

import adliya.uz.referenceservice.config.AzureTranslatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates English interface strings into one provider language per request ({@code textType=html} with protected
 * placeholders, see {@link InterfaceTextProtection}). Called only by the background worker, never while serving a
 * dictionary. The subscription key is sent only in the request header.
 */
@Component
@Slf4j
public class AzureInterfaceTranslator {

    private final AzureTranslatorProperties properties;
    private final RestClient client;

    @Autowired
    public AzureInterfaceTranslator(AzureTranslatorProperties properties) {
        this(properties, defaultClient());
    }

    AzureInterfaceTranslator(AzureTranslatorProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    public boolean configured() {
        return properties.configured();
    }

    /**
     * One translation per input text, in order; an element is null when the provider's answer was rejected by
     * {@link InterfaceTextProtection#restore}. Throws {@link ProviderException} when the request fails.
     */
    public List<String> translate(List<String> englishTexts, String providerCode) {
        if (englishTexts.isEmpty()) {
            return List.of();
        }
        if (!configured()) {
            throw new ProviderException(503, "Azure Translator is not configured");
        }
        AzureResponse[] response;
        try {
            response = client.post()
                    .uri(uri(providerCode))
                    .header("Ocp-Apim-Subscription-Key", properties.getKey())
                    .header("Ocp-Apim-Subscription-Region", properties.getRegion())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(englishTexts.stream().map(text -> Map.of("Text", InterfaceTextProtection.protect(text))).toList())
                    .retrieve()
                    .body(AzureResponse[].class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("Azure Translator returned HTTP {} for target {}", status, providerCode);
            throw new ProviderException(status, "Azure Translator returned HTTP " + status);
        } catch (ResourceAccessException exception) {
            throw new ProviderException(0, "Azure Translator did not respond");
        } catch (RuntimeException exception) {
            log.warn("Azure Translator request failed ({})", exception.getClass().getSimpleName());
            throw new ProviderException(502, "Azure Translator request failed");
        }
        if (response == null || response.length != englishTexts.size()) {
            throw new ProviderException(502, "Azure Translator returned an incomplete answer");
        }
        List<String> result = new ArrayList<>(englishTexts.size());
        for (int i = 0; i < response.length; i++) {
            AzureResponse item = response[i];
            String html = item == null || item.translations() == null || item.translations().isEmpty()
                    ? null : item.translations().get(0).text();
            result.add(InterfaceTextProtection.restore(englishTexts.get(i), html).orElse(null));
        }
        return result;
    }

    private URI uri(String providerCode) {
        return UriComponentsBuilder.fromUriString(properties.getEndpoint())
                .path("/translate")
                .queryParam("api-version", "3.0")
                .queryParam("from", "en")
                .queryParam("to", providerCode)
                .queryParam("textType", "html")
                .build()
                .encode()
                .toUri();
    }

    private static RestClient defaultClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);
        return RestClient.builder().requestFactory(factory).build();
    }

    private record AzureResponse(List<AzureTranslation> translations) {}

    private record AzureTranslation(String text, String to) {}
}
