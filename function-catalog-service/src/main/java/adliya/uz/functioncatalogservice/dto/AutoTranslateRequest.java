package adliya.uz.functioncatalogservice.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record AutoTranslateRequest(
        @NotEmpty @Size(max = 20) List<@NotNull @Pattern(regexp = "[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*") @Size(max = 35) String> languages,
        boolean overwriteMachine
) {}
