package adliya.uz.functioncatalogservice.security;

import java.util.List;

public record JwtPrincipal(
        String email,
        String role,
        List<Long> organizationIds,
        List<String> permissions,
        Long userId
) {
    public JwtPrincipal {
        organizationIds = organizationIds == null ? List.of() : List.copyOf(organizationIds);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public JwtPrincipal(String email, String role, List<Long> organizationIds, List<String> permissions) {
        this(email, role, organizationIds, permissions, null);
    }

    public boolean isSuperAdmin() {
        return "ROLE_SUPER_ADMIN".equals(role);
    }
}
