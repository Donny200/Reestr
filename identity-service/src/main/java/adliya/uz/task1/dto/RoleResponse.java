package adliya.uz.task1.dto;

import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {
    private Long id;
    private String name;
    private List<PermissionResponse> permissions;

    public static RoleResponse from(Role role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(PermissionResponse::from)
                .toList();
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .permissions(permissions)
                .build();
    }
}
