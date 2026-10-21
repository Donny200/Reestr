package adliya.uz.task1.dto;

import java.util.Map;

/**
 * Human translations of an organization's official name and description, keyed by BCP 47 language tag. A provided map
 * replaces that field completely; an omitted (null) map leaves it unchanged; a blank value removes that language.
 */
public record UpdateOrganizationTranslationsRequest(
        Map<String, String> nameTranslations,
        Map<String, String> descriptionTranslations
) {
}
