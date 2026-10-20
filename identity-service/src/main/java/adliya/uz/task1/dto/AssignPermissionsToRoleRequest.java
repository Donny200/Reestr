package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class AssignPermissionsToRoleRequest {

    @NotNull(message = "permissionIds is required (can be empty to clear)")
    private Set<@NotNull(message = "Permission ID is required")
            @Positive(message = "Permission ID must be positive") Long> permissionIds;
}
