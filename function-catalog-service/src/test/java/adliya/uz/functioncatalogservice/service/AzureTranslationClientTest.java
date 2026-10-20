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
    private AzureTranslatorProperties properties() {
        var p = new AzureTranslatorProperties(); p.setEndpoint("https://translator.example"); p.setKey("test-only"); p.setRegion("test-region"); return p;
    }
}
