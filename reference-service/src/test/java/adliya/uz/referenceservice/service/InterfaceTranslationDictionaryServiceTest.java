package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.repository.TranslationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterfaceTranslationDictionaryServiceTest {

    private final TranslationRepository translationRepository = mock(TranslationRepository.class);
    private final InterfaceTranslationDictionaryService service =
            new InterfaceTranslationDictionaryService(translationRepository);

    @Test
    void requestedLanguageOverridesEnglishAndMissingValuesFallBack() {
        when(translationRepository.findActiveDictionary("en")).thenReturn(List.of(
                translation("nav.home", "en", "Home"),
                translation("action.save", "en", "Save")
        ));
        when(translationRepository.findActiveDictionary("ru")).thenReturn(List.of(
                translation("nav.home", "ru", "Главная")
        ));

        Map<String, String> result = service.getTranslations("ru");

        assertThat(result).containsEntry("nav.home", "Главная");
        assertThat(result).containsEntry("action.save", "Save");
        verify(translationRepository).findActiveDictionary("en");
        verify(translationRepository).findActiveDictionary("ru");
    }

    @Test
    void regionalLanguageUsesBaseLanguageBeforeExactVariant() {
        when(translationRepository.findActiveDictionary("en")).thenReturn(List.of(
                translation("nav.home", "en", "Home"),
                translation("action.save", "en", "Save")
        ));
        when(translationRepository.findActiveDictionary("uz")).thenReturn(List.of(
                translation("nav.home", "uz", "Bosh sahifa"),
                translation("action.save", "uz", "Saqlash")
        ));
        when(translationRepository.findActiveDictionary("uz-Cyrl")).thenReturn(List.of(
                translation("nav.home", "uz-Cyrl", "Bosh sahifa (Cyrl)")
        ));

        Map<String, String> result = service.getTranslations("uz-Cyrl");

        assertThat(result).containsEntry("nav.home", "Bosh sahifa (Cyrl)");
        assertThat(result).containsEntry("action.save", "Saqlash");
        verify(translationRepository).findActiveDictionary("en");
        verify(translationRepository).findActiveDictionary("uz");
        verify(translationRepository).findActiveDictionary("uz-Cyrl");
    }

    private Translation translation(String keyName, String languageCode, String value) {
        TranslationKey key = TranslationKey.builder()
                .id((long) keyName.hashCode())
                .translationKey(keyName)
                .active(true)
                .build();
        return Translation.builder()
                .translationKey(key)
                .languageCode(languageCode)
                .translationValue(value)
                .build();
    }
}
