package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AzureTranslatorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class AzureTranslationClientTest {
    @Test void reusesAzureProtocolAndReturnsRealTargets() {
        var properties = properties();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=ru&to=en&to=uz"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Ocp-Apim-Subscription-Key", "test-only"))
                .andExpect(content().json("[{\"Text\":\"Юридическая консультация\"}]"))
                .andRespond(withSuccess("[{\"translations\":[{\"to\":\"en\",\"text\":\"Legal consultation\"},{\"to\":\"uz\",\"text\":\"Yuridik maslahat\"}]}]", MediaType.APPLICATION_JSON));
        assertThat(new AzureTranslationClient(properties, builder.build()).translate("Юридическая консультация", "ru", List.of("en","uz","ru","en")))
                .containsEntry("en","Legal consultation").containsEntry("uz","Yuridik maslahat").hasSize(2);
        server.verify();
    }
    @Test void missingConfigurationDoesNotInventMachineTranslations() {
        assertThat(new AzureTranslationClient(new AzureTranslatorProperties())
                .translate("Text", "ru", List.of("en","uz"))).isEmpty();
    }
    @Test void failureLeavesTranslationsForRetry() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=ru&to=en"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(new AzureTranslationClient(properties(), builder.build()).translate("Text","ru",List.of("en"))).isEmpty();
        server.verify();
    }
    @Test void strictClientReportsProviderAuthFailureWithoutLeakingResponseOrKeys() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("sensitive-provider-body"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AzureTranslationClient(properties(), builder.build()).translateRequired("Text","en",List.of("ru")))
                .isInstanceOf(adliya.uz.functioncatalogservice.exception.TranslationUnavailableException.class)
                .hasMessageNotContaining("sensitive-provider-body").hasMessageNotContaining("test-only");
        server.verify();
    }
    @Test void batchSendsEveryTextInOneRequestAndKeepsOrder() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=en&to=ru&to=uz"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"Text\":\"Name\"},{\"Text\":\"Description\"}]"))
                .andRespond(withSuccess("[{\"translations\":[{\"to\":\"ru\",\"text\":\"Имя\"},{\"to\":\"uz\",\"text\":\"Nom\"}]},"
                        + "{\"translations\":[{\"to\":\"ru\",\"text\":\"Описание\"}]}]", MediaType.APPLICATION_JSON));
        var result = new AzureTranslationClient(properties(), builder.build()).translateBatch(List.of("Name", "Description"), "en", List.of("ru", "uz", "en"));
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("ru", "Имя").containsEntry("uz", "Nom");
        assertThat(result.get(1)).containsOnlyKeys("ru");
        server.verify();
    }
    @Test void rateLimitIsReportedAsSuchSoTheQueueBacksOffLonger() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AzureTranslationClient(properties(), builder.build()).translateBatch(List.of("Text"), "en", List.of("ru")))
                .isInstanceOfSatisfying(adliya.uz.functioncatalogservice.exception.TranslationUnavailableException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        server.verify();
    }
    @Test void unsupportedTargetsAreLeftOutWithoutFailingSupportedOnes() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=en&to=ru&to=fr"))
                .andRespond(withSuccess("[{\"translations\":[{\"to\":\"ru\",\"text\":\"Паспорт\"},{\"to\":\"fr\",\"text\":\"Passeport\"}]}]",
                        MediaType.APPLICATION_JSON));
        var client = new AzureTranslationClient(properties(), builder.build());
        var result = client.translateBatch(List.of("Passport"), "en", List.of("ru", "uz-cyrl", "fr"));
        assertThat(result.get(0)).containsOnly(org.assertj.core.api.Assertions.entry("ru", "Паспорт"),
                org.assertj.core.api.Assertions.entry("fr", "Passeport"));
        assertThat(client.unsupportedTargets("en", List.of("ru", "uz-cyrl", "fr", "en"))).containsExactly("uz-cyrl");
        server.verify();
    }

    @Test void variantsSharingAProviderCodeAreRequestedOnceAndFannedOut() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=en&to=fr&to=fr-CA&to=zh-Hant"))
                .andRespond(withSuccess("[{\"translations\":[{\"to\":\"fr\",\"text\":\"Passeport\"},"
                        + "{\"to\":\"fr-CA\",\"text\":\"Passeport (CA)\"},{\"to\":\"zh-Hant\",\"text\":\"護照\"}]}]", MediaType.APPLICATION_JSON));
        var result = new AzureTranslationClient(properties(), builder.build())
                .translateRequired("Passport", "en", List.of("fr", "fr-fr", "fr-ca", "zh-tw", "en-gb"));
        assertThat(result).containsEntry("fr", "Passeport").containsEntry("fr-fr", "Passeport")
                .containsEntry("fr-ca", "Passeport (CA)").containsEntry("zh-tw", "護照").doesNotContainKey("en-gb");
        server.verify();
    }

    @Test void aTargetAzureRejectsIsIsolatedSoTheOthersAreStillTranslated() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String invalidTarget = "{\"error\":{\"code\":400036,\"message\":\"The target language is not valid.\"}}";
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=en&to=ru&to=de"))
                .andRespond(withBadRequest().body(invalidTarget).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=en&to=ru"))
                .andRespond(withSuccess("[{\"translations\":[{\"to\":\"ru\",\"text\":\"Паспорт\"}]}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://translator.example/translate?api-version=3.0&from=en&to=de"))
                .andRespond(withBadRequest().body(invalidTarget).contentType(MediaType.APPLICATION_JSON));
        var support = adliya.uz.functioncatalogservice.translation.AzureLanguageSupport.offline();
        var client = new AzureTranslationClient(properties(), builder.build(), support);
        assertThat(client.translateRequired("Passport", "en", List.of("ru", "de"))).containsOnlyKeys("ru");
        assertThat(client.unsupportedTargets("en", List.of("ru", "de"))).containsExactly("de");
        server.verify();
    }

    @Test void unsupportedSourceLanguageIsNeverSentToTheProvider() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AzureTranslationClient(properties(), builder.build());
        assertThat(client.sourceUnsupported("uz-cyrl")).isTrue();
        assertThat(client.sourceUnsupported("uz")).isFalse();
        assertThat(client.translateBatch(List.of("Паспорт"), "uz-cyrl", List.of("ru", "en"))).containsExactly(java.util.Map.of());
        assertThat(client.unsupportedTargets("uz-cyrl", List.of("ru", "en"))).containsExactlyInAnyOrder("ru", "en");
        server.verify(); // no request at all
    }

    @Test void providerKeyNeverAppearsInErrorsOrLogs(org.springframework.boot.test.system.CapturedOutput output) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withBadRequest().body("{\"error\":{\"code\":400050,\"message\":\"echo test-only\"}}"));
        server.expect(anything()).andRespond(withServerError().body("test-only"));
        var client = new AzureTranslationClient(properties(), builder.build());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.translateRequired("Text", "en", List.of("ru")))
                .isInstanceOf(adliya.uz.functioncatalogservice.exception.TranslationUnavailableException.class)
                .hasMessageNotContaining("test-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.translateRequired("Text", "en", List.of("ru")))
                .hasMessageNotContaining("test-only");
        assertThat(client.unsupportedTargets("en", List.of("ru"))).isEmpty(); // a non-language 400 is not a rejection
        assertThat(output.getAll()).doesNotContain("test-only");
        server.verify();
    }

    private AzureTranslatorProperties properties() {
        var p = new AzureTranslatorProperties(); p.setEndpoint("https://translator.example"); p.setKey("test-only"); p.setRegion("test-region"); return p;
    }
}
