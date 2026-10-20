package adliya.uz.functioncatalogservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ActiveLanguageClientTest {

    @Test void activeLanguagesAreNormalizedAndCached() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo("http://identity-service/api/languages"))
                .andRespond(withSuccess("[{\"code\":\"EN\"},{\"code\":\" ru \"},{\"code\":null},{\"code\":\"uz\"}]",
                        MediaType.APPLICATION_JSON));
        ActiveLanguageClient client = new ActiveLanguageClient(builder);

        assertThat(client.activeLanguageCodes()).contains(Set.of("en", "ru", "uz"));
        assertThat(client.activeLanguageCodes()).contains(Set.of("en", "ru", "uz"));
        server.verify();
    }

    @Test void unavailableDirectoryMeansTranslationsCannotBeJudged() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://identity-service/api/languages")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://identity-service/api/languages")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        ActiveLanguageClient client = new ActiveLanguageClient(builder);

        assertThat(client.activeLanguageCodes()).isEmpty();
        assertThat(client.activeLanguageCodes()).isEmpty();
        server.verify();
    }
}
