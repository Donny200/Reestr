package adliya.uz.task1.config.security;

import adliya.uz.task1.entity.User;

import java.util.Arrays;

public enum SystemRole {
    SUPER_ADMIN("ROLE_SUPER_ADMIN"),
    ORG_ADMIN("ROLE_ORG_ADMIN"),
    MODERATOR("ROLE_MODERATOR");

    private final String authority;

    SystemRole(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }

    public boolean isHeldBy(User user) {
        return user.getRole() != null && authority.equals(user.getRole().getName());
    }

    public static boolean isSystemRole(String roleName) {
        return Arrays.stream(values())
                .anyMatch(role -> role.authority.equals(roleName));
    }
}
