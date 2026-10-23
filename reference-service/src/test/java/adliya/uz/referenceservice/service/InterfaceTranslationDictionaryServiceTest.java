package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.repository.TranslationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void regionalVariantOverlaysItsParentLanguage() {
        when(translationRepository.findActiveDictionary("en")).thenReturn(List.of(
                translation("nav.home", "en", "Home"),
                translation("action.save", "en", "Save")
        ));
        when(translationRepository.findActiveDictionary("fr")).thenReturn(List.of(
                translation("nav.home", "fr", "Accueil"),
                translation("action.save", "fr", "Enregistrer")
        ));
        when(translationRepository.findActiveDictionary("fr-CA")).thenReturn(List.of(
                translation("action.save", "fr-CA", "Sauvegarder")
        ));

        Map<String, String> result = service.getTranslations("fr-CA");

        assertThat(result).containsEntry("nav.home", "Accueil").containsEntry("action.save", "Sauvegarder");
    }

    @Test
    void otherScriptOfTheLanguageIsNeverUsedAsFallback() {
        when(translationRepository.findActiveDictionary("en")).thenReturn(List.of(
                translation("nav.home", "en", "Home"),
                translation("action.save", "en", "Save")
        ));
        when(translationRepository.findActiveDictionary("uz")).thenReturn(List.of(
                translation("nav.home", "uz", "Bosh sahifa"),
                translation("action.save", "uz", "Saqlash")
        ));
        when(translationRepository.findActiveDictionary("uz-Cyrl")).thenReturn(List.of(
                translation("nav.home", "uz-Cyrl", "Бош саҳифа")
        ));

        Map<String, String> cyrillic = service.getTranslations("uz-Cyrl-UZ");

        // Latin Uzbek is not Cyrillic Uzbek: a missing Cyrillic value is shown in English until it is translated.
        assertThat(cyrillic).containsEntry("nav.home", "Бош саҳифа").containsEntry("action.save", "Save");
        verify(translationRepository, never()).findActiveDictionary("uz");
        // The usual script inherits the plain language tag.
        assertThat(service.getTranslations("uz-Latn-UZ")).containsEntry("action.save", "Saqlash");
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
