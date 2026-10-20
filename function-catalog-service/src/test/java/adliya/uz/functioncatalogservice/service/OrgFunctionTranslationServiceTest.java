package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrgFunctionTranslationServiceTest {

    private final OrgFunctionTranslationService service = new OrgFunctionTranslationService();

    @Test
    void removesOnlyMachineNameTranslationsWhenNameChanges() {
        Map<String, TranslatedText> nameTranslations = new LinkedHashMap<>();
        nameTranslations.put("ru", new TranslatedText("machine name", TranslatedText.MACHINE));
        nameTranslations.put("uz", new TranslatedText("human name", TranslatedText.HUMAN));
        nameTranslations.put("fr", null);
        Map<String, TranslatedText> descriptionTranslations = new LinkedHashMap<>();
        descriptionTranslations.put("ru", new TranslatedText("machine description", TranslatedText.MACHINE));

        OrgFunction function = OrgFunction.builder()
                .nameTranslations(nameTranslations)
                .descriptionTranslations(descriptionTranslations)
                .build();

        service.invalidateMachineTranslations(function, true, false);

        assertFalse(function.getNameTranslations().containsKey("ru"));
        assertEquals(nameTranslations.get("uz"), function.getNameTranslations().get("uz"));
        assertTrue(function.getNameTranslations().containsKey("fr"));
        assertSame(descriptionTranslations, function.getDescriptionTranslations());
    }

    @Test
    void removesOnlyMachineDescriptionTranslationsWhenDescriptionChanges() {
        Map<String, TranslatedText> nameTranslations = Map.of(
                "ru", new TranslatedText("machine name", TranslatedText.MACHINE)
        );
        Map<String, TranslatedText> descriptionTranslations = new LinkedHashMap<>();
        descriptionTranslations.put("ru", new TranslatedText("machine description", TranslatedText.MACHINE));
        descriptionTranslations.put("uz", new TranslatedText("human description", TranslatedText.HUMAN));

        OrgFunction function = OrgFunction.builder()
                .nameTranslations(nameTranslations)
                .descriptionTranslations(descriptionTranslations)
                .build();

        service.invalidateMachineTranslations(function, false, true);

        assertSame(nameTranslations, function.getNameTranslations());
        assertFalse(function.getDescriptionTranslations().containsKey("ru"));
        assertEquals(descriptionTranslations.get("uz"), function.getDescriptionTranslations().get("uz"));
    }

    @Test
    void initializesMissingTranslationMapWhenChanged() {
        OrgFunction function = OrgFunction.builder()
                .nameTranslations(null)
                .descriptionTranslations(null)
                .build();

        service.invalidateMachineTranslations(function, true, true);

        assertTrue(function.getNameTranslations().isEmpty());
        assertTrue(function.getDescriptionTranslations().isEmpty());
    }

    @Test
    void changedInstructionKeepsHumanTranslationsAndClearedInstructionDropsAll() {
        Map<String, Map<String, TranslatedText>> translations = new LinkedHashMap<>();
        translations.put("steps", new LinkedHashMap<>(Map.of(
                "ru", new TranslatedText("Шаг", TranslatedText.HUMAN),
                "uz", new TranslatedText("Qadam", TranslatedText.MACHINE))));
        translations.put("fee", new LinkedHashMap<>(Map.of("ru", new TranslatedText("Бесплатно", TranslatedText.HUMAN))));
        translations.put("whoCanUse", new LinkedHashMap<>(Map.of("uz", new TranslatedText("Fuqarolar", TranslatedText.MACHINE))));
        OrgFunction function = OrgFunction.builder()
                .sourceLanguage("en")
                .steps("New step")
                .whoCanUse("Citizens")
                .instructionTranslations(translations)
                .build();

        service.invalidateInstructionTranslations(function, Set.of(InstructionField.STEPS, InstructionField.FEE), false);

        assertEquals(Map.of("ru", new TranslatedText("Шаг", TranslatedText.HUMAN)),
                function.getInstructionTranslations().get("steps"));
        assertFalse(function.getInstructionTranslations().containsKey("fee"));
        assertEquals(TranslatedText.MACHINE, function.getInstructionTranslations().get("whoCanUse").get("uz").source());
    }

    @Test
    void sourceLanguageChangeDropsMachineInstructionTranslationsAndTheNewOriginalLanguage() {
        Map<String, Map<String, TranslatedText>> translations = new LinkedHashMap<>();
        translations.put("whoCanUse", new LinkedHashMap<>(Map.of(
                "ru", new TranslatedText("Граждане", TranslatedText.HUMAN),
                "uz", new TranslatedText("Fuqarolar", TranslatedText.MACHINE),
                "en", new TranslatedText("Citizens", TranslatedText.HUMAN))));
        OrgFunction function = OrgFunction.builder()
                .sourceLanguage("ru")
                .whoCanUse("Граждане")
                .instructionTranslations(translations)
                .build();

        service.invalidateInstructionTranslations(function, List.of(), true);

        assertEquals(Set.of("en"), function.getInstructionTranslations().get("whoCanUse").keySet());
    }
}
