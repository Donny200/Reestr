package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.repository.TranslationKeyRepository;
import adliya.uz.referenceservice.repository.TranslationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterfaceTranslationServiceTest {

    private final TranslationKeyRepository translationKeyRepository = mock(TranslationKeyRepository.class);
    private final TranslationRepository translationRepository = mock(TranslationRepository.class);
    private final InterfaceTranslationDictionaryService dictionaryService = mock(InterfaceTranslationDictionaryService.class);
    private final InterfaceTranslationService service = new InterfaceTranslationService(
            translationKeyRepository,
            translationRepository,
            dictionaryService,
            new LanguageTagNormalizer()
    );

    @Test
    void rejectsUnknownKeysWithoutWriting() {
        when(translationKeyRepository.findAllByTranslationKeyInAndActiveTrue(anyCollection()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.updateTranslations("ru", Map.of("unknown.key", "Значение")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown.key");

        verify(translationRepository, never()).saveAll(anyCollection());
    }

    @Test
    void insertsKnownKeyUsingCanonicalLanguageTag() {
        TranslationKey key = TranslationKey.builder()
                .id(1L)
                .translationKey("nav.home")
                .active(true)
                .build();
        when(translationKeyRepository.findAllByTranslationKeyInAndActiveTrue(anyCollection()))
                .thenReturn(List.of(key));
        when(translationRepository.findAllByLanguageAndKeyIds("uz-Cyrl", List.of(1L)))
                .thenReturn(List.of());

        service.updateTranslations("uz_cyrl", Map.of("nav.home", "Бош саҳифа"));

        verify(translationRepository).findAllByLanguageAndKeyIds("uz-Cyrl", List.of(1L));
        verify(translationRepository).saveAll(anyCollection());
    }
}
