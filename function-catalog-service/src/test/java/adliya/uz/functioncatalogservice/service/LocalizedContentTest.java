package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedContentTest {

    @Test void variantsFallBackThroughTheirParentTagsBeforeTheSource() {
        Map<String, TranslatedText> names = Map.of("fr", new TranslatedText("Passeport", TranslatedText.MACHINE),
                "uz", new TranslatedText("Pasport", TranslatedText.HUMAN));
        assertThat(LocalizedContent.text("Passport", names, "fr-FR")).isEqualTo("Passeport");
        assertThat(LocalizedContent.text("Passport", names, "uz-latn-uz")).isEqualTo("Pasport");
        assertThat(LocalizedContent.text("Passport", names, "de-at")).isEqualTo("Passport");
        assertThat(LocalizedContent.hierarchy("uz-Latn-UZ")).containsExactly("uz-latn-uz", "uz-latn", "uz");
    }

    @Test void instructionVariantUsesTheParentTranslationOrTheSourceLanguage() {
        OrgFunction function = new OrgFunction();
        function.setSourceLanguage("en");
        function.setSteps("Apply");
        Map<String, Map<String, TranslatedText>> instructions = new LinkedHashMap<>();
        instructions.put("steps", Map.of("fr", new TranslatedText("Déposer", TranslatedText.MACHINE)));
        function.setInstructionTranslations(instructions);

        LocalizedContent.Instruction french = LocalizedContent.instruction(function, InstructionField.STEPS, "fr-ca");
        assertThat(french.text()).isEqualTo("Déposer");
        assertThat(french.machine()).isTrue();
        assertThat(french.fallback()).isFalse();
        LocalizedContent.Instruction british = LocalizedContent.instruction(function, InstructionField.STEPS, "en-gb");
        assertThat(british.text()).isEqualTo("Apply");
        assertThat(british.fallback()).isFalse();
        assertThat(LocalizedContent.instruction(function, InstructionField.STEPS, "ru").fallback()).isTrue();
    }
}
