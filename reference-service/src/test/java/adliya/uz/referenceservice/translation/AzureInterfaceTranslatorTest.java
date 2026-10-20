package adliya.uz.referenceservice.translation;

import adliya.uz.referenceservice.config.AzureTranslatorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AzureInterfaceTranslatorTest {

    private static final String KEY = "secret-subscription-key";

    private MockRestServiceServer server;
    private AzureInterfaceTranslator translator;

    @BeforeEach
    void setUp() {
        AzureTranslatorProperties properties = new AzureTranslatorProperties();
        properties.setEndpoint("https://api.cognitive.microsofttranslator.com");
        properties.setKey(KEY);
        properties.setRegion("westeurope");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        translator = new AzureInterfaceTranslator(properties, builder.build());
    }

    @Test
    void sendsOneHtmlRequestPerTargetWithProtectedPlaceholders() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.cognitive.microsofttranslator.com/translate")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("from", "en"))
                .andExpect(queryParam("to", "zh-Hans"))
                .andExpect(queryParam("textType", "html"))
                .andExpect(header("Ocp-Apim-Subscription-Key", KEY))
                .andExpect(header("Ocp-Apim-Subscription-Region", "westeurope"))
                .andExpect(content().json("[{\"Text\":\"Save\"},{\"Text\":\"Every <span class=\\\"notranslate\\\">{seconds}</span> s\"}]"))
                .andRespond(withSuccess("[{\"translations\":[{\"text\":\"保存\",\"to\":\"zh-Hans\"}]},"
                        + "{\"translations\":[{\"text\":\"每 <span class=\\\"notranslate\\\">{seconds}</span> 秒\",\"to\":\"zh-Hans\"}]}]",
                        MediaType.APPLICATION_JSON));

        assertThat(translator.translate(List.of("Save", "Every {seconds} s"), "zh-Hans"))
                .containsExactly("保存", "每 {seconds} 秒");
        server.verify();
    }

    @Test
    void answerThatDropsAPlaceholderBecomesNull() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.cognitive.microsofttranslator.com/translate")))
                .andRespond(withSuccess("[{\"translations\":[{\"text\":\"Toutes les secondes\",\"to\":\"fr\"}]}]",
                        MediaType.APPLICATION_JSON));
        assertThat(translator.translate(List.of("Every {seconds} s"), "fr")).containsExactly((String) null);
    }

    @Test
    void providerErrorsCarryTheStatusButNeverTheKey() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.cognitive.microsofttranslator.com/translate")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":429001}}"));
        assertThatThrownBy(() -> translator.translate(List.of("Save"), "fr"))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.rateLimited()).isTrue();
                    assertThat(exception.getMessage()).doesNotContain(KEY);
                });
    }

    @Test
    void refusesToRunWithoutCredentials() {
        AzureTranslatorProperties empty = new AzureTranslatorProperties();
        AzureInterfaceTranslator unconfigured = new AzureInterfaceTranslator(empty, RestClient.create());
        assertThat(unconfigured.configured()).isFalse();
        assertThatThrownBy(() -> unconfigured.translate(List.of("Save"), "fr")).isInstanceOf(ProviderException.class);
        assertThat(empty.toString()).doesNotContain("null-key");
    }

    @Test
    void propertiesNeverPrintTheKey() {
        AzureTranslatorProperties properties = new AzureTranslatorProperties();
        properties.setKey(KEY);
        assertThat(properties.toString()).doesNotContain(KEY);
    }
}
