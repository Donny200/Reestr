package adliya.uz.functioncatalogservice.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MissingTranslationsTest {

    @Test void regionalVariantsOfTheSourceNeedNoTranslationButOtherScriptsDo() {
        assertThat(MissingTranslations.targets(Set.of("en", "en-gb", "fr", "fr-fr", "uz", "uz-cyrl", "pt-pt"), "fr"))
                .containsExactly("en", "en-gb", "pt-pt", "uz", "uz-cyrl");
        assertThat(MissingTranslations.targets(Set.of("uz", "uz-latn-uz", "uz-cyrl", "ru"), "uz"))
                .containsExactly("ru", "uz-cyrl");
        assertThat(MissingTranslations.sameWrittenLanguage("pt", "pt-pt")).isFalse();
        assertThat(MissingTranslations.sameWrittenLanguage("zh-tw", "zh-hant")).isTrue();
    }
}
