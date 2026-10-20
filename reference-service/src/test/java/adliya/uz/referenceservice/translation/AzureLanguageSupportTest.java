package adliya.uz.referenceservice.translation;

import adliya.uz.referenceservice.config.AzureTranslatorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AzureLanguageSupportTest {

    private static AzureTranslatorProperties properties() {
        AzureTranslatorProperties properties = new AzureTranslatorProperties();
        properties.setEndpoint("https://api.cognitive.microsofttranslator.com");
        properties.setKey("secret");
        properties.setRegion("westeurope");
        return properties;
    }

    @Test
    void usesTheLiveListWithoutSendingTheKeyAndCachesIt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.cognitive.microsofttranslator.com/languages?api-version=3.0&scope=translation"))
                .andExpect(headerDoesNotExist("Ocp-Apim-Subscription-Key"))
                .andRespond(withSuccess("{\"translation\":{\"en\":{},\"fr\":{},\"zh-Hans\":{}}}", MediaType.APPLICATION_JSON));
        AzureLanguageSupport support = new AzureLanguageSupport(properties(), builder.build(),
                Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC));

        assertThat(support.providerCode("fr-FR")).contains("fr");
        assertThat(support.providerCode("zh-CN")).contains("zh-Hans");
        assertThat(support.providerCode("ru")).isEmpty();
        server.verify();
    }

    @Test
    void fallsBackToTheBuiltInListWhenTheProviderListIsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.cognitive.microsofttranslator.com/languages")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        AzureLanguageSupport support = new AzureLanguageSupport(properties(), builder.build(), Clock.systemUTC());

        assertThat(support.providerCodes()).isEqualTo(AzureLanguageCodes.KNOWN_PROVIDER_CODES);
        assertThat(support.providerCode("ru")).contains("ru");
        assertThat(support.providerCode("uz-Cyrl")).isEmpty();
    }
}
