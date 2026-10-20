package adliya.uz.task1.service;

import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.TranslatedText;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrganizationTranslationStateService {

    public void invalidateMachineTranslations(
            Organization organization,
            boolean nameChanged,
            boolean descriptionChanged
    ) {
        if (nameChanged) {
            organization.setNameTranslations(withoutMachineTranslations(organization.getNameTranslations()));
        }
        if (descriptionChanged) {
            organization.setDescriptionTranslations(withoutMachineTranslations(organization.getDescriptionTranslations()));
        }
    }

    private Map<String, TranslatedText> withoutMachineTranslations(Map<String, TranslatedText> existing) {
        Map<String, TranslatedText> retained = existing == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existing);
        retained.entrySet().removeIf(entry -> entry.getValue() != null
                && TranslatedText.MACHINE.equals(entry.getValue().source()));
        return retained;
    }
}
