package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.AssignRoleRequest;
import adliya.uz.task1.dto.CreateRoleRequest;
import adliya.uz.task1.dto.RoleResponse;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.RoleRepository;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private RoleService roleService;

    @Test
    void createNormalizesRoleNameAndIgnoresClientControlledPermissions() {
        when(roleRepository.findByName("ROLE_CONTENT_EDITOR")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(10L);
            return role;
        });

        RoleResponse response = roleService.create(new CreateRoleRequest(" content-editor "));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("ROLE_CONTENT_EDITOR");
        assertThat(response.getPermissions()).isEmpty();
    }

    @Test
    void createRejectsDuplicateNormalizedRoleName() {
        when(roleRepository.findByName("ROLE_AUDITOR"))
                .thenReturn(Optional.of(role(7L, "ROLE_AUDITOR")));

        assertThatThrownBy(() -> roleService.create(new CreateRoleRequest("auditor")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void deleteRejectsSystemRole() {
        Role role = role(1L, SystemRole.ORG_ADMIN.authority());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("System role");

        verify(userRepository, never()).existsByRole_Id(any());
        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    void deleteRejectsRoleAssignedToUser() {
        Role role = role(4L, "ROLE_AUDITOR");
        when(roleRepository.findById(4L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole_Id(4L)).thenReturn(true);

        assertThatThrownBy(() -> roleService.delete(4L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assigned to users");

        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    void deleteAllowsUnassignedCustomRole() {
        Role role = role(4L, "ROLE_AUDITOR");
        when(roleRepository.findById(4L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole_Id(4L)).thenReturn(false);

        roleService.delete(4L);

        verify(roleRepository).delete(role);
    }

    @Test
    void assignRejectsSelfAssignmentOfSuperAdmin() {
        Role currentRole = role(3L, SystemRole.MODERATOR.authority());
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        User user = user(15L, "actor@reestr.uz", currentRole, true);
        when(userRepository.findById(15L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(superAdminRole));

        assertThatThrownBy(() -> roleService.assignRoleToUser(
                new AssignRoleRequest(15L, 1L),
                "ACTOR@reestr.uz"
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("yourself");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void assignRejectsDowngradeOfLastActiveSuperAdmin() {
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        Role moderatorRole = role(3L, SystemRole.MODERATOR.authority());
        User target = user(20L, "last-admin@reestr.uz", superAdminRole, true);
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(moderatorRole));
        when(userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(1L);

        assertThatThrownBy(() -> roleService.assignRoleToUser(
                new AssignRoleRequest(20L, 3L),
                "another-admin@reestr.uz"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last active SUPER_ADMIN");

        assertThat(target.getRole()).isSameAs(superAdminRole);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void assignAllowsDowngradeWhenAnotherActiveSuperAdminExists() {
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        Role moderatorRole = role(3L, SystemRole.MODERATOR.authority());
        User target = user(20L, "admin@reestr.uz", superAdminRole, true);
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(moderatorRole));
        when(userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(2L);

        String result = roleService.assignRoleToUser(
                new AssignRoleRequest(20L, 3L),
                "another-admin@reestr.uz"
        );

        assertThat(target.getRole()).isSameAs(moderatorRole);
        assertThat(result).contains("ROLE_MODERATOR", "admin@reestr.uz");
        verify(userRepository).save(target);
    }

    private Role role(Long id, String name) {
        return Role.builder()
                .id(id)
                .name(name)
                .build();
    }

    private User user(Long id, String email, Role role, boolean enabled) {
        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("User")
                .email(email)
                .password("encoded")
                .role(role)
                .enabled(enabled)
                .build();
    }
}
