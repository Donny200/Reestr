package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public record FunctionCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) Map<@Pattern(regexp = "[a-z]{2,3}(-[a-z0-9]{2,8})*") String,
                @NotBlank @Size(max = 100) String> nameTranslations,
        /** Language of {@code name}; optional, defaults to en. Other active languages are machine-filled in the background. */
        @Pattern(regexp = "[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*") @Size(max = 35) String sourceLanguage
) {
    public FunctionCategoryRequest(String name, Map<String, String> nameTranslations) {
        this(name, nameTranslations, null);
    }
}
