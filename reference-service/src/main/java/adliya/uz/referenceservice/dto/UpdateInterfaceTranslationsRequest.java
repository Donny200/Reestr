package adliya.uz.referenceservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateInterfaceTranslationsRequest(
        @NotEmpty(message = "Translations must not be empty")
        @Valid
        Map<
                @NotBlank(message = "Translation key must not be blank")
                @Size(max = 150, message = "Translation key is too long") String,
                @NotBlank(message = "Translation value must not be blank") String
                > translations
) {
}
