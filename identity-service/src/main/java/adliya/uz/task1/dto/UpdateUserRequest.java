package adliya.uz.task1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Pattern(regexp = ".*\\S.*", message = "First name must not be blank")
    @Size(max = 70, message = "First name must not exceed 70 characters")
    private String firstName;

    @Pattern(regexp = ".*\\S.*", message = "Last name must not be blank")
    @Size(max = 70, message = "Last name must not exceed 70 characters")
    private String lastName;

    @Pattern(regexp = ".*\\S.*", message = "Email must not be blank")
    @Email(message = "Invalid email")
    @Size(max = 120, message = "Email must not exceed 120 characters")
    private String email;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
}
