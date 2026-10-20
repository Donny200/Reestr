package adliya.uz.task1.dto;

import adliya.uz.task1.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {
    private Long id;
    private String code;
    private String name;
    private String category;

    public static PermissionResponse from(Permission p) {
        return PermissionResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .category(p.getCategory())
                .build();
    }
}