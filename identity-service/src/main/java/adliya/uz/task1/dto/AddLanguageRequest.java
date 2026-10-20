package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddLanguageRequest(
        @NotBlank(message = "Language code is required")
        @Size(max = 64, message = "Language code must be at most 64 characters")
        String code,
        @Size(max = 100, message = "Native name must be at most 100 characters")
        String nativeName
) {}
