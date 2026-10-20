package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record LanguageTranslationRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Size(max = 6) Map<@NotBlank @Size(max = 40) String, @Size(max = 4000) String> instructions
) {
    public LanguageTranslationRequest(String name, String description) {
        this(name, description, null);
    }
}
