package adliya.uz.task1.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreatePermissionRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 60)
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 60)
    private String category;
}