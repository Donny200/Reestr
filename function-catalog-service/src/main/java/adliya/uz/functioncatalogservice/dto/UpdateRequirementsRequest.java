package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRequirementsRequest(
        @NotBlank(message = "Requirements text is required") String requirements
) {}
