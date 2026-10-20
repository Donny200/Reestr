package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrgFunctionTranslationService {

    public void invalidateMachineTranslations(OrgFunction function, boolean nameChanged, boolean descriptionChanged) {
        if (nameChanged) {
            function.setNameTranslations(withoutMachineTranslations(function.getNameTranslations()));
        }
        if (descriptionChanged) {
            function.setDescriptionTranslations(withoutMachineTranslations(function.getDescriptionTranslations()));
        }
    }

    public void invalidateInstructionTranslations(OrgFunction function, Collection<InstructionField> changed, boolean languageChanged) {
        if (changed.isEmpty() && !languageChanged) {
            return;
        }
        Map<String, Map<String, TranslatedText>> result = new LinkedHashMap<>();
        for (InstructionField field : InstructionField.values()) {
            Map<String, TranslatedText> values = function.instructionTranslationsOf(field);
            if (field.get(function) == null) {
                continue;
            }
            Map<String, TranslatedText> kept = changed.contains(field) || languageChanged
                    ? withoutMachineTranslations(values)
                    : new LinkedHashMap<>(values);
            kept.remove(function.getSourceLanguage());
            if (!kept.isEmpty()) {
                result.put(field.key(), kept);
            }
        }
        function.setInstructionTranslations(result);
    }

    static Map<String, TranslatedText> withoutMachineTranslations(Map<String, TranslatedText> translations) {
        Map<String, TranslatedText> preserved = translations == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(translations);
        preserved.entrySet().removeIf(entry ->
                entry.getValue() != null && TranslatedText.MACHINE.equals(entry.getValue().source()));
        return preserved;
    }
}
