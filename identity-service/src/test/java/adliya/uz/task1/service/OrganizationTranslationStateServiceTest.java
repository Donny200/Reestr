package adliya.uz.task1.service;

import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.TranslatedText;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationTranslationStateServiceTest {

    private final OrganizationTranslationStateService service = new OrganizationTranslationStateService();

    @Test
    void changedNameRemovesOnlyMachineNameTranslations() {
        Map<String, TranslatedText> names = new LinkedHashMap<>();
        names.put("ru", new TranslatedText("Организация", TranslatedText.MACHINE));
        names.put("uz", new TranslatedText("Tashkilot", TranslatedText.HUMAN));
        names.put("fr", new TranslatedText("Organisation", "imported"));

        Map<String, TranslatedText> descriptions = new LinkedHashMap<>();
        descriptions.put("ru", new TranslatedText("Описание", TranslatedText.MACHINE));

        Organization organization = Organization.builder()
                .nameTranslations(names)
                .descriptionTranslations(descriptions)
                .build();

        service.invalidateMachineTranslations(organization, true, false);

        assertThat(organization.getNameTranslations())
                .containsOnlyKeys("uz", "fr")
                .containsEntry("uz", new TranslatedText("Tashkilot", TranslatedText.HUMAN));
        assertThat(organization.getDescriptionTranslations()).containsKey("ru");
    }

    @Test
    void changedDescriptionHandlesMissingTranslations() {
        Organization organization = Organization.builder()
                .descriptionTranslations(null)
                .build();

        service.invalidateMachineTranslations(organization, false, true);

        assertThat(organization.getDescriptionTranslations()).isEmpty();
    }
}
