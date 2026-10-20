package adliya.uz.referenceservice.dto;

import jakarta.validation.constraints.Size;

public record UpdateTranslationKeyRequest(
        @Size(max = 500, message = "Description is too long")
        String description,
        Boolean required,
        Boolean active
) {
}
