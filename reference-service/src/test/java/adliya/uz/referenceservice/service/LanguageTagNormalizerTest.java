package adliya.uz.referenceservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageTagNormalizerTest {

    private final LanguageTagNormalizer normalizer = new LanguageTagNormalizer();

    @Test
    void canonicalizesBcp47Tags() {
        assertThat(normalizer.normalize(" uz_cyrl ")).isEqualTo("uz-Cyrl");
        assertThat(normalizer.normalize("PT-br")).isEqualTo("pt-BR");
    }

    @Test
    void rejectsBlankAndMalformedTags() {
        assertThatThrownBy(() -> normalizer.normalize(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize("not--a-tag"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCanonicalTagsThatDoNotFitTheDatabaseColumn() {
        String longTag = "en-x-abcdefgh-abcdefgh-abcdefgh-abcdefgh-abcdefgh-abcdefgh-abcdefgh";

        assertThatThrownBy(() -> normalizer.normalize(longTag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }
}
