package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 30, message = "Role name must not exceed 30 characters")
    @Pattern(
            regexp = "(?i)^\\s*(?:ROLE_)?[A-Z][A-Z0-9]*(?:[_ -][A-Z0-9]+)*\\s*$",
            message = "Role name may contain only letters, digits, spaces, hyphens, and underscores"
    )
    private String name;
}
