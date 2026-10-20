package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.*;
public record RejectFunctionRequest(@NotBlank @Size(max = 2000) String reason) {}
