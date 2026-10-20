package adliya.uz.referenceservice.dto;

import adliya.uz.referenceservice.entity.TranslationKey;

public record TranslationKeyResponse(
        Long id,
        String translationKey,
        String description,
        boolean required,
        boolean active,
        String defaultValue
) {
    public static TranslationKeyResponse from(TranslationKey key, String defaultValue) {
        return new TranslationKeyResponse(
                key.getId(),
                key.getTranslationKey(),
                key.getDescription(),
                key.isRequired(),
                key.isActive(),
                defaultValue
        );
    }
}
