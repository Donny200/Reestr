package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Disabling or regenerating codes requires the password and a current code (or a recovery code). */
public record MfaDisableRequest(@NotBlank String password, @NotBlank @Size(max = 32) String code) {}
