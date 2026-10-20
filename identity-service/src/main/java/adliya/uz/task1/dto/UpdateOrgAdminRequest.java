package adliya.uz.task1.dto;

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
public class UpdateOrgAdminRequest {

    @Size(max = 70)
    private String firstName;

    @Size(max = 70)
    private String lastName;

    @Size(max = 20)
    private String phone;

    private Long organizationId;

    private Boolean enabled;
}
