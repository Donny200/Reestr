package adliya.uz.functioncatalogservice.translation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AzureLanguageCodesTest {

    private static final Set<String> KNOWN = AzureLanguageCodes.KNOWN_PROVIDER_CODES;

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "en, en",
            "en-GB, en",
            "fr, fr",
            "fr-FR, fr",
            "fr-fr, fr",
            "fr-BE, fr",
            "fr-CA, fr-CA",
            "pt, pt",
            "pt-BR, pt",
            "pt-PT, pt-PT",
            "pt-AO, pt-PT",
            "uz, uz",
            "uz-Latn, uz",
            "uz-Latn-UZ, uz",
            "uz-UZ, uz",
            "ru, ru",
            "ru-RU, ru",
            "zh, zh-Hans",
            "zh-CN, zh-Hans",
            "zh-Hans, zh-Hans",
            "zh-hans, zh-Hans",
            "zh-TW, zh-Hant",
            "zh-HK, zh-Hant",
            "zh-Hant-TW, zh-Hant",
            "sr, sr-Cyrl",
            "sr-Cyrl, sr-Cyrl",
            "sr-Latn, sr-Latn",
            "sr-Latn-RS, sr-Latn",
            "mn, mn-Cyrl",
            "mn-Mong, mn-Mong",
            "iu, iu",
            "iu-Latn, iu-Latn",
            "tl, fil",
            "fil, fil",
            "no, nb",
            "nb-NO, nb",
            "he, he",
            "ckb, ku",
            "ku, kmr",
            "kk, kk",
            "pa-Guru-IN, pa",
            "tlh-Latn, tlh-Latn",
            "de_AT, de",
    })
    void mapsApplicationTagsToProviderCodes(String tag, String expected) {
        assertThat(AzureLanguageCodes.providerCode(tag, KNOWN)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"uz-Cyrl", "uz-cyrl", "uz-Arab", "az-Cyrl", "pa-Arab", "bs-Cyrl", "kk-Latn", "zh-Latn",
            "sr-Arab", "nn", "tlh", "xx", "und", "", "not a tag"})
    void reportsTagsTheProviderCannotTranslate(String tag) {
        assertThat(AzureLanguageCodes.providerCode(tag, KNOWN)).isEmpty();
    }

    @Test
    void usesTheLiveProviderListInsteadOfAssumingSupport() {
        Set<String> live = Set.of("en", "fr", "uz");
        assertThat(AzureLanguageCodes.providerCode("ru", live)).isEmpty();
        assertThat(AzureLanguageCodes.providerCode("fr-CA", live)).contains("fr");
        assertThat(AzureLanguageCodes.providerCode("de", Set.of("en"))).isEmpty();
    }

    @Test
    void returnsTheProviderSpellingOfCodes() {
        assertThat(AzureLanguageCodes.providerCode("ZH-hant", Set.of("zh-Hant"))).isEqualTo(Optional.of("zh-Hant"));
        assertThat(AzureLanguageCodes.providerCode("sr-latn", Set.of("sr-Latn"))).contains("sr-Latn");
    }

    @Test
    void canonicalizesStoredTags() {
        assertThat(AzureLanguageCodes.canonical("uz-cyrl")).isEqualTo("uz-Cyrl");
        assertThat(AzureLanguageCodes.canonical("pt_br")).isEqualTo("pt-BR");
        assertThat(AzureLanguageCodes.canonical("zh-hant-tw")).isEqualTo("zh-Hant-TW");
        assertThat(AzureLanguageCodes.canonical("")).isNull();
        assertThat(AzureLanguageCodes.canonical("??")).isNull();
    }
}
