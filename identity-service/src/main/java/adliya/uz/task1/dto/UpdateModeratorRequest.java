package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotEmpty;
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
public class UpdateModeratorRequest {

    @Size(max = 70)
    private String firstName;

    @Size(max = 70)
    private String lastName;

    @Size(max = 20)
    private String phone;

    @NotEmpty(message = "At least one organizationId is required")
    private Set<Long> organizationIds;

    private Boolean enabled;
}
