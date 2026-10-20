package adliya.uz.task1.dto;

import jakarta.validation.constraints.*;
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
public class CreateModeratorRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 70)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 70)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100)
    private String password;

    @Size(max = 20)
    private String phone;

    @NotEmpty(message = "At least one organizationId is required")
    private Set<Long> organizationIds;
}
