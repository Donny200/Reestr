package adliya.uz.task1.service;

import adliya.uz.task1.dto.AddLanguageRequest;
import adliya.uz.task1.entity.Language;
import adliya.uz.task1.repository.LanguageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageServiceTest {

    @Mock
    private LanguageRepository languageRepository;

    @InjectMocks
    private LanguageService languageService;

    @ParameterizedTest
    @CsvSource({
            "pt-br, pt-BR",
            "uz-cyrl, uz-Cyrl",
            "uz_cyrl, uz-Cyrl",
            "zh-hans, zh-Hans",
            "sr-latn-rs, sr-Latn-RS"
    })
    void addCanonicalizesBcp47Tags(String input, String expected) {
        when(languageRepository.existsByCodeIgnoreCase(expected)).thenReturn(false);
        when(languageRepository.save(any(Language.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Language language = languageService.add(new AddLanguageRequest(input, null));

        assertThat(language.getCode()).isEqualTo(expected);
        assertThat(language.getNameNative()).isNotBlank();
        assertThat(language.isActive()).isTrue();
        verify(languageRepository).existsByCodeIgnoreCase(expected);
    }

    @Test
    void addUsesProvidedNativeName() {
        when(languageRepository.existsByCodeIgnoreCase("uz-Cyrl")).thenReturn(false);
        when(languageRepository.save(any(Language.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Language language = languageService.add(new AddLanguageRequest("uz-Cyrl", "  Ўзбекча  "));

        assertThat(language.getNameNative()).isEqualTo("Ўзбекча");
    }

    @Test
    void addRejectsMalformedBcp47Tag() {
        assertThatThrownBy(() -> languageService.add(new AddLanguageRequest("en--US", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BCP 47");
    }

    @Test
    void addRejectsUndeterminedLanguage() {
        assertThatThrownBy(() -> languageService.add(new AddLanguageRequest("und", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BCP 47");
    }

    @Test
    void derivedNativeNameAlwaysFitsTheDatabaseColumn() {
        String code = "en-u-ca-islamic-civil-co-phonebk-cu-usd-fw-mon-hc-h24-nu-latn";
        when(languageRepository.existsByCodeIgnoreCase(code)).thenReturn(false);
        when(languageRepository.save(any(Language.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Language language = languageService.add(new AddLanguageRequest(code, null));

        assertThat(language.getNameNative()).isNotBlank();
        assertThat(language.getNameNative()).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void addRejectsLanguageCodeThatDoesNotFitTheDatabaseColumn() {
        String longTag = "en-x-abcdefgh-abcdefgh-abcdefgh-abcdefgh-abcdefgh-abcdefgh-abcdefgh";

        assertThatThrownBy(() -> languageService.add(new AddLanguageRequest(longTag, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }
}
