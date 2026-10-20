package adliya.uz.referenceservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTranslationKeyRequest(
        @NotBlank(message = "Translation key is required")
        @Size(max = 150, message = "Translation key is too long")
        String translationKey,

        @Size(max = 500, message = "Description is too long")
        String description,

        Boolean required,

        @NotBlank(message = "Default English value is required")
        String defaultValue
) {
}
