package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public record FunctionTranslationsRequest(
        @Size(max = 100) Map<@Pattern(regexp = "[a-z]{2,3}(-[a-z0-9]{2,8})*") String,
                @NotBlank @Size(max = 150) String> nameTranslations,
        @Size(max = 100) Map<@Pattern(regexp = "[a-z]{2,3}(-[a-z0-9]{2,8})*") String,
                @NotBlank @Size(max = 500) String> descriptionTranslations
) {}
