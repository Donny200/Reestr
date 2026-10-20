package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotNull;
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
public class PromoteToOrgAdminRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "organizationId is required")
    private Long organizationId;
}
