package adliya.uz.functioncatalogservice.translation;

import adliya.uz.functioncatalogservice.config.AzureTranslatorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AzureLanguageSupportTest {

    private static final String LANGUAGES = "https://translator.example/languages?api-version=3.0&scope=translation";

    @Test void usesTheLiveProviderListWithoutSendingTheKeyAndCachesIt() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(LANGUAGES))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Ocp-Apim-Subscription-Key"))
                .andRespond(withSuccess("{\"translation\":{\"en\":{\"name\":\"English\"},\"fr\":{\"name\":\"French\"},\"uz\":{\"name\":\"Uzbek\"}}}",
                        MediaType.APPLICATION_JSON));
        var support = new AzureLanguageSupport(properties(), builder.build(), Clock.systemUTC());

        assertThat(support.providerCode("fr-FR")).contains("fr");
        assertThat(support.supports("uz")).isTrue();
        assertThat(support.supports("ru")).isFalse(); // not offered by this (live) list
        assertThat(support.supports("uz-Cyrl")).isFalse();
        assertThat(support.providerCodes()).containsExactlyInAnyOrder("en", "fr", "uz");
        server.verify(); // fetched once
    }

    @Test void fallsBackToTheBuiltInListAndRetriesAfterAFewMinutes() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(LANGUAGES)).andRespond(withServerError());
        server.expect(once(), requestTo(LANGUAGES)).andRespond(withSuccess("{\"translation\":{\"en\":{}}}", MediaType.APPLICATION_JSON));
        var clock = new MutableClock(Instant.parse("2026-10-20T10:00:00Z"));
        var support = new AzureLanguageSupport(properties(), builder.build(), clock);

        assertThat(support.providerCodes()).isEqualTo(AzureLanguageCodes.KNOWN_PROVIDER_CODES);
        assertThat(support.supports("ru")).isTrue();
        clock.advance(Duration.ofMinutes(4));
        assertThat(support.supports("ru")).isTrue(); // failure cached, no second request yet
        clock.advance(Duration.ofMinutes(2));
        assertThat(support.supports("ru")).isFalse(); // refreshed list
        server.verify();
    }

    @Test void rejectedCodesAreUnsupportedUntilTheNextRefresh() {
        var support = AzureLanguageSupport.offline();
        assertThat(support.supports("de")).isTrue();
        support.markRejected("de");
        assertThat(support.supports("de-AT")).isFalse();
        assertThat(support.supports("fr")).isTrue();
    }

    @Test void unsetEndpointUsesTheBuiltInListWithoutAnyRequest() {
        var support = new AzureLanguageSupport(new AzureTranslatorProperties(), RestClient.builder().build(), Clock.systemUTC());
        assertThat(support.providerCodes()).isEqualTo(AzureLanguageCodes.KNOWN_PROVIDER_CODES);
    }

    private static AzureTranslatorProperties properties() {
        var properties = new AzureTranslatorProperties();
        properties.setEndpoint("https://translator.example");
        properties.setKey("test-only");
        properties.setRegion("test-region");
        return properties;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
