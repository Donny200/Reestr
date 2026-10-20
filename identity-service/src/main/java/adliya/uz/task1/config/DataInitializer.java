package adliya.uz.task1.config;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.repository.PermissionRepository;
import adliya.uz.task1.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(0)
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final List<String> ROLE_NAMES = Arrays.stream(SystemRole.values()).map(SystemRole::authority).toList();
    private static final List<String> ORG_ADMIN_PERMISSIONS = List.of(
            "ORGANIZATIONS_VIEW", "MODERATORS_VIEW", "MODERATORS_CREATE",
            "MODERATORS_EDIT", "MODERATORS_DEACTIVATE", "REPORTS_VIEW", "REPORTS_MANAGE",
            "ORG_ANALYTICS_VIEW", "ORG_REPORTS_MANAGE", "FUNCTIONS_ASSIGN", "VISITOR_QUESTIONS_VIEW", "VISITOR_QUESTIONS_REPLY",
            "APPOINTMENTS_VIEW", "APPOINTMENTS_MANAGE");
    private static final List<String> MODERATOR_PERMISSIONS = List.of("ORGANIZATIONS_VIEW", "REPORTS_VIEW", "VISITOR_QUESTIONS_VIEW",
            "APPOINTMENTS_VIEW");
    private static final List<PermissionSeed> PERMISSION_SEEDS = List.of(
            new PermissionSeed("ORGANIZATIONS_VIEW", "View organizations", "Organization Management"),
            new PermissionSeed("ORGANIZATIONS_CREATE", "Create organizations", "Organization Management"),
            new PermissionSeed("ORGANIZATIONS_EDIT", "Edit organizations", "Organization Management"),
            new PermissionSeed("ORGANIZATIONS_DEACTIVATE", "Deactivate organizations", "Organization Management"),
            new PermissionSeed("ORGANIZATIONS_REACTIVATE", "Reactivate organizations", "Organization Management"),
            new PermissionSeed("ORGANIZATIONS_EDIT_OWN", "Edit own organization's profile", "Organization Management"),

            new PermissionSeed("ORG_ADMINS_VIEW", "View org admins", "Org Admin Management"),
            new PermissionSeed("ORG_ADMINS_CREATE", "Create org admins", "Org Admin Management"),
            new PermissionSeed("ORG_ADMINS_EDIT", "Edit org admins", "Org Admin Management"),
            new PermissionSeed("ORG_ADMINS_DEACTIVATE", "Deactivate org admins", "Org Admin Management"),

            new PermissionSeed("MODERATORS_VIEW", "View moderators", "Moderator Management"),
            new PermissionSeed("MODERATORS_CREATE", "Create moderators", "Moderator Management"),
            new PermissionSeed("MODERATORS_EDIT", "Edit moderators", "Moderator Management"),
            new PermissionSeed("MODERATORS_DEACTIVATE", "Deactivate moderators", "Moderator Management"),

            new PermissionSeed("USERS_VIEW", "View all users", "User Management"),
            new PermissionSeed("USERS_CREATE", "Create users", "User Management"),
            new PermissionSeed("USERS_EDIT", "Edit users", "User Management"),
            new PermissionSeed("USERS_DEACTIVATE", "Deactivate users", "User Management"),

            new PermissionSeed("LANGUAGES_VIEW", "Search language catalog", "Language Management"),
            new PermissionSeed("LANGUAGES_CREATE", "Add languages", "Language Management"),
            new PermissionSeed("LANGUAGES_DELETE", "Delete languages", "Language Management"),

            new PermissionSeed("FUNCTIONS_VIEW", "View functions (admin)", "Function Management"),
            new PermissionSeed("FUNCTIONS_CREATE", "Create organization functions", "Function Management"),
            new PermissionSeed("FUNCTIONS_EDIT", "Edit organization functions", "Function Management"),
            new PermissionSeed("FUNCTIONS_MANAGE_REQUIREMENTS", "Edit function requirements", "Function Management"),
            new PermissionSeed("FUNCTIONS_DEACTIVATE", "Deactivate functions", "Function Management"),
            new PermissionSeed("FUNCTIONS_MANAGE_ANY_ORGANIZATION", "Manage functions across all organizations", "Function Management"),
            new PermissionSeed("FUNCTIONS_SUBMIT_REVIEW", "Submit functions for review", "Function Management"),
            new PermissionSeed("FUNCTIONS_REVIEW", "Review function drafts", "Function Management"),
            new PermissionSeed("FUNCTIONS_PUBLISH", "Publish reviewed functions", "Function Management"),
            new PermissionSeed("FUNCTIONS_REACTIVATE", "Return deactivated functions to review", "Function Management"),
            new PermissionSeed("FUNCTIONS_TRANSLATIONS_EDIT", "Edit function translations", "Function Management"),
            new PermissionSeed("FUNCTION_CATEGORIES_MANAGE", "Manage function categories", "Function Management"),
            new PermissionSeed("FUNCTIONS_IMPORT", "Import function drafts", "Function Management"),
            new PermissionSeed("AUDIT_VIEW", "View function audit history", "Function Management"),
            new PermissionSeed("FUNCTIONS_ASSIGN", "Assign the staff member responsible for a function", "Function Management"),

            new PermissionSeed("TRANSLATION_KEYS_VIEW", "View translation keys", "Translation Management"),
            new PermissionSeed("TRANSLATION_KEYS_CREATE", "Create translation keys", "Translation Management"),
            new PermissionSeed("TRANSLATION_KEYS_EDIT", "Edit translation keys", "Translation Management"),
            new PermissionSeed("TRANSLATION_KEYS_DEACTIVATE", "Deactivate translation keys", "Translation Management"),
            new PermissionSeed("TRANSLATIONS_EDIT", "Edit interface translations", "Translation Management"),
            new PermissionSeed("TRANSLATIONS_VIEW_COVERAGE", "View translation coverage", "Translation Management"),

            new PermissionSeed("ROLES_VIEW", "View roles", "Role & Permission Management"),
            new PermissionSeed("ROLES_MANAGE_PERMISSIONS", "Assign permissions to roles", "Role & Permission Management"),
            new PermissionSeed("ROLES_CREATE", "Create custom roles", "Role & Permission Management"),
            new PermissionSeed("ROLES_DELETE", "Delete custom roles", "Role & Permission Management"),
            new PermissionSeed("ROLES_ASSIGN", "Assign roles to users", "Role & Permission Management"),

            new PermissionSeed("REPORTS_VIEW", "View reports", "Reports"),
            new PermissionSeed("REPORTS_MANAGE", "Review and resolve information reports", "Reports"),
            new PermissionSeed("ORG_REPORTS_MANAGE", "Handle visitor reports for own organizations", "Reports"),

            new PermissionSeed("ORG_ANALYTICS_VIEW", "View organization dashboard, catalog engagement and quality queue", "Analytics"),

            new PermissionSeed("VISITOR_QUESTIONS_VIEW", "View visitor questions for own organizations", "Visitor questions"),
            new PermissionSeed("VISITOR_QUESTIONS_REPLY", "Reply to, assign and close visitor questions", "Visitor questions"),

            new PermissionSeed("APPOINTMENTS_VIEW", "View appointment bookings of own organizations", "Appointments"),
            new PermissionSeed("APPOINTMENTS_MANAGE", "Manage appointment availability and bookings of own organizations", "Appointments")
    );

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public DataInitializer(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ROLE_NAMES.forEach(this::ensureRole);
        PERMISSION_SEEDS.forEach(this::ensurePermission);
        grantAllPermissions(SystemRole.SUPER_ADMIN.authority());
        grantIfUnassigned(SystemRole.ORG_ADMIN.authority(), ORG_ADMIN_PERMISSIONS);
        grantIfUnassigned(SystemRole.MODERATOR.authority(), MODERATOR_PERMISSIONS);
    }

    private void ensureRole(String roleName) {
        if (roleRepository.findByName(roleName).isEmpty()) {
            Role role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
            log.info("Role {} created", roleName);
        }
    }

    private void ensurePermission(PermissionSeed seed) {
        if (permissionRepository.findByCode(seed.code()).isEmpty()) {
            permissionRepository.save(Permission.builder()
                    .code(seed.code())
                    .name(seed.name())
                    .category(seed.category())
                    .build());
        }
    }

    private void grantAllPermissions(String roleName) {
        Role role = requireRole(roleName);
        Set<Permission> all = new HashSet<>(permissionRepository.findAll());
        if (!codesOf(all).equals(codesOf(role.getPermissions()))) {
            role.setPermissions(all);
            roleRepository.save(role);
        }
    }

    private void grantIfUnassigned(String roleName, List<String> permissionCodes) {
        Role role = requireRole(roleName);
        if (!role.getPermissions().isEmpty()) {
            return;
        }
        Set<Permission> permissions = new HashSet<>();
        permissionCodes.forEach(code -> permissionRepository.findByCode(code).ifPresent(permissions::add));
        role.setPermissions(permissions);
        roleRepository.save(role);
    }

    private Role requireRole(String roleName) {
        return roleRepository.findByName(roleName).orElseThrow();
    }

    private static Set<String> codesOf(Collection<Permission> permissions) {
        return permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
    }

    private record PermissionSeed(String code, String name, String category) {}
}
