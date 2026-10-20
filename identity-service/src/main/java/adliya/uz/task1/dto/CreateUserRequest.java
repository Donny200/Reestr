package adliya.uz.task1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 70, message = "First name must not exceed 70 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 70, message = "Last name must not exceed 70 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    @Size(max = 120, message = "Email must not exceed 120 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @NotNull(message = "roleId is required")
    @Positive(message = "roleId must be positive")
    private Long roleId;

    @NotNull(message = "organizationIds is required")
    private Set<@Valid @Positive(message = "organizationId must be positive") Long> organizationIds;
}
