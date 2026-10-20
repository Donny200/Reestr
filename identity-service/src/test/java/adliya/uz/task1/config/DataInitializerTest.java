package adliya.uz.task1.config;

import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.repository.PermissionRepository;
import adliya.uz.task1.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Test
    void addsMissingPermissionsToExistingSuperAdminOnly() throws Exception {
        Permission existingPermission = Permission.builder()
                .id(1L)
                .code("ORGANIZATIONS_VIEW")
                .name("View organizations")
                .category("Organization Management")
                .build();
        Map<String, Permission> catalogue = new LinkedHashMap<>();
        catalogue.put(existingPermission.getCode(), existingPermission);

        Role superAdmin = role(1L, "ROLE_SUPER_ADMIN", Set.of(existingPermission));
        Role orgAdmin = role(2L, "ROLE_ORG_ADMIN", Set.of(existingPermission));
        Role moderator = role(3L, "ROLE_MODERATOR", Set.of(existingPermission));

        when(roleRepository.findByName("ROLE_SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(roleRepository.findByName("ROLE_ORG_ADMIN")).thenReturn(Optional.of(orgAdmin));
        when(roleRepository.findByName("ROLE_MODERATOR")).thenReturn(Optional.of(moderator));
        when(permissionRepository.findByCode(any())).thenAnswer(invocation ->
                Optional.ofNullable(catalogue.get(invocation.getArgument(0))));
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> {
            Permission saved = invocation.getArgument(0);
            saved.setId((long) catalogue.size() + 1);
            catalogue.put(saved.getCode(), saved);
            return saved;
        });
        when(permissionRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(catalogue.values()));

        new DataInitializer(roleRepository, permissionRepository).run();

        assertThat(catalogue).hasSize(55);
        assertThat(catalogue.get("VISITOR_QUESTIONS_VIEW").getCategory()).isEqualTo("Visitor questions");
        assertThat(catalogue.get("APPOINTMENTS_VIEW").getCategory()).isEqualTo("Appointments");
        assertThat(catalogue.get("APPOINTMENTS_MANAGE").getCategory()).isEqualTo("Appointments");
        assertThat(catalogue.get("VISITOR_QUESTIONS_REPLY").getCategory()).isEqualTo("Visitor questions");
        assertThat(catalogue.get("REPORTS_MANAGE").getCategory()).isEqualTo("Reports");
        assertThat(catalogue.get("ORG_REPORTS_MANAGE").getCategory()).isEqualTo("Reports");
        assertThat(catalogue.get("ORG_ANALYTICS_VIEW").getCategory()).isEqualTo("Analytics");
        assertThat(catalogue.values().stream().filter(p -> p.getCategory().equals("Function Management")).map(Permission::getCode))
                .contains("FUNCTIONS_CREATE", "FUNCTIONS_EDIT", "FUNCTIONS_DEACTIVATE",
                        "FUNCTIONS_SUBMIT_REVIEW", "FUNCTIONS_REVIEW", "FUNCTIONS_PUBLISH",
                        "FUNCTIONS_REACTIVATE", "FUNCTIONS_TRANSLATIONS_EDIT",
                        "FUNCTION_CATEGORIES_MANAGE", "FUNCTIONS_IMPORT", "AUDIT_VIEW");
        assertThat(superAdmin.getPermissions())
                .extracting(Permission::getCode)
                .contains("ORGANIZATIONS_VIEW", "USERS_VIEW", "FUNCTIONS_EDIT", "TRANSLATIONS_EDIT")
                .hasSize(catalogue.size());
        assertThat(orgAdmin.getPermissions())
                .extracting(Permission::getCode)
                .containsExactly("ORGANIZATIONS_VIEW");
        assertThat(moderator.getPermissions())
                .extracting(Permission::getCode)
                .containsExactly("ORGANIZATIONS_VIEW");
        verify(roleRepository).save(superAdmin);
        verify(roleRepository, never()).save(orgAdmin);
        verify(roleRepository, never()).save(moderator);
    }

    @Test
    void freshOrgAdminGetsDashboardAndReportHandlingButModeratorStaysReadOnly() throws Exception {
        Map<String, Permission> catalogue = new LinkedHashMap<>();
        Role superAdmin = role(1L, "ROLE_SUPER_ADMIN", Set.of());
        Role orgAdmin = role(2L, "ROLE_ORG_ADMIN", Set.of());
        Role moderator = role(3L, "ROLE_MODERATOR", Set.of());

        when(roleRepository.findByName("ROLE_SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(roleRepository.findByName("ROLE_ORG_ADMIN")).thenReturn(Optional.of(orgAdmin));
        when(roleRepository.findByName("ROLE_MODERATOR")).thenReturn(Optional.of(moderator));
        when(permissionRepository.findByCode(any())).thenAnswer(invocation ->
                Optional.ofNullable(catalogue.get(invocation.getArgument(0))));
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> {
            Permission saved = invocation.getArgument(0);
            saved.setId((long) catalogue.size() + 1);
            catalogue.put(saved.getCode(), saved);
            return saved;
        });
        when(permissionRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(catalogue.values()));

        new DataInitializer(roleRepository, permissionRepository).run();

        assertThat(orgAdmin.getPermissions()).extracting(Permission::getCode)
                .contains("ORG_ANALYTICS_VIEW", "ORG_REPORTS_MANAGE", "REPORTS_VIEW", "VISITOR_QUESTIONS_VIEW", "VISITOR_QUESTIONS_REPLY",
                        "APPOINTMENTS_VIEW", "APPOINTMENTS_MANAGE");
        assertThat(moderator.getPermissions()).extracting(Permission::getCode)
                .containsExactlyInAnyOrder("ORGANIZATIONS_VIEW", "REPORTS_VIEW", "VISITOR_QUESTIONS_VIEW", "APPOINTMENTS_VIEW");
        assertThat(superAdmin.getPermissions()).extracting(Permission::getCode)
                .contains("ORG_ANALYTICS_VIEW", "ORG_REPORTS_MANAGE");
    }

    private Role role(Long id, String name, Set<Permission> permissions) {
        return Role.builder()
                .id(id)
                .name(name)
                .permissions(new java.util.HashSet<>(permissions))
                .build();
    }
}
