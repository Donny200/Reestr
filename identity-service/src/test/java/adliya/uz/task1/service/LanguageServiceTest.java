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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
        when(languageRepository.save(any(Language.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Language language = languageService.add(new AddLanguageRequest(input, null));

        assertThat(language.getCode()).isEqualTo(expected);
        assertThat(language.getNameNative()).isNotBlank();
        assertThat(language.isActive()).isTrue();
        verify(languageRepository).findByCodeIgnoreCase(expected);
    }

    @Test
    void addUsesProvidedNativeName() {
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

    @Test
    void removeDeactivatesTheLanguageAndKeepsItsRow() {
        Language french = Language.builder().id(4L).code("fr").nameNative("Français").active(true).build();
        when(languageRepository.findById(4L)).thenReturn(Optional.of(french));

        languageService.remove(4L);

        assertThat(french.isActive()).isFalse();
        verify(languageRepository).save(french);
        verify(languageRepository, never()).delete(any(Language.class));
    }

    @Test
    void removingTheDefaultLanguageIsRefused() {
        Language english = Language.builder().id(1L).code("en").nameNative("English").isDefault(true).active(true).build();
        when(languageRepository.findById(1L)).thenReturn(Optional.of(english));

        assertThatThrownBy(() -> languageService.remove(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(english.isActive()).isTrue();
    }

    @Test
    void addingARemovedLanguageAgainReactivatesTheSameRowByCanonicalTag() {
        Language removed = Language.builder().id(7L).code("uz-cyrl").nameNative("Ўзбекча").active(false).build();
        when(languageRepository.findByCodeIgnoreCase("uz-Cyrl")).thenReturn(Optional.of(removed));
        when(languageRepository.save(any(Language.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Language restored = languageService.add(new AddLanguageRequest("UZ_CYRL", null));

        assertThat(restored).isSameAs(removed);
        assertThat(restored.getId()).isEqualTo(7L);
        assertThat(restored.getCode()).isEqualTo("uz-Cyrl");
        assertThat(restored.isActive()).isTrue();
        assertThat(restored.getNameNative()).isEqualTo("Ўзбекча");
    }

    @Test
    void addingAnActiveLanguageAgainIsAConflict() {
        when(languageRepository.findByCodeIgnoreCase("pt-BR")).thenReturn(Optional.of(
                Language.builder().id(9L).code("pt-BR").nameNative("Português").active(true).build()));

        assertThatThrownBy(() -> languageService.add(new AddLanguageRequest("pt-br", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already enabled");
    }

    @Test
    void searchOffersRemovedLanguagesAgain() {
        when(languageRepository.findAllByActiveTrue()).thenReturn(List.of(
                Language.builder().id(1L).code("en").nameNative("English").active(true).build()));

        assertThat(languageService.search("français")).filteredOn(result -> result.code().equals("fr"))
                .singleElement().satisfies(result -> assertThat(result.alreadyAdded()).isFalse());
        assertThat(languageService.search("english")).filteredOn(result -> result.code().equals("en"))
                .singleElement().satisfies(result -> assertThat(result.alreadyAdded()).isTrue());
    }
}
