package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.dto.CreateTranslationKeyRequest;
import adliya.uz.referenceservice.dto.TranslationKeyResponse;
import adliya.uz.referenceservice.entity.Translation;
import adliya.uz.referenceservice.entity.TranslationKey;
import adliya.uz.referenceservice.repository.TranslationKeyRepository;
import adliya.uz.referenceservice.repository.TranslationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationKeyServiceTest {

    @Mock
    private TranslationKeyRepository translationKeyRepository;

    @Mock
    private TranslationRepository translationRepository;

    @InjectMocks
    private TranslationKeyService service;

    @Test
    void createStoresKeyAndDefaultEnglishTranslationAtomically() {
        when(translationKeyRepository.findByTranslationKey("nav.help")).thenReturn(Optional.empty());
        when(translationKeyRepository.save(any(TranslationKey.class))).thenAnswer(invocation -> {
            TranslationKey key = invocation.getArgument(0);
            key.setId(17L);
            return key;
        });

        TranslationKeyResponse response = service.create(new CreateTranslationKeyRequest(
                " nav.help ",
                "Help navigation item",
                true,
                " Help "
        ));

        ArgumentCaptor<Translation> translationCaptor = ArgumentCaptor.forClass(Translation.class);
        verify(translationRepository).save(translationCaptor.capture());
        Translation savedTranslation = translationCaptor.getValue();
        assertThat(savedTranslation.getLanguageCode()).isEqualTo("en");
        assertThat(savedTranslation.getTranslationValue()).isEqualTo("Help");
        assertThat(savedTranslation.getTranslationKey().getTranslationKey()).isEqualTo("nav.help");
        assertThat(response.defaultValue()).isEqualTo("Help");
    }

    @Test
    void deactivatePreservesKeyAndStoredTranslations() {
        TranslationKey key = TranslationKey.builder()
                .id(5L)
                .translationKey("nav.home")
                .active(true)
                .build();
        when(translationKeyRepository.findById(5L)).thenReturn(Optional.of(key));

        service.deactivate(5L);

        assertThat(key.isActive()).isFalse();
        verify(translationKeyRepository).save(key);
    }

    @Test
    void adminListIncludesDefaultValueForInactiveKey() {
        TranslationKey key = TranslationKey.builder()
                .id(9L)
                .translationKey("nav.legacy")
                .active(false)
                .build();
        Translation english = Translation.builder()
                .translationKey(key)
                .languageCode("en")
                .translationValue("Legacy")
                .build();
        when(translationRepository.findDictionary("en")).thenReturn(List.of(english));
        when(translationKeyRepository.findAll()).thenReturn(List.of(key));

        List<TranslationKeyResponse> response = service.getAll();

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.active()).isFalse();
            assertThat(item.defaultValue()).isEqualTo("Legacy");
        });
    }
}
