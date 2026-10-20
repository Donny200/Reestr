package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class PromoteToModeratorRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotEmpty(message = "At least one organizationId is required")
    private Set<Long> organizationIds;
}

