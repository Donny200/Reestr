package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.AssignRoleRequest;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceSecurityTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private RoleService roleService;

    @Test
    void userCannotAssignSuperAdminRoleToSelf() {
        Role currentRole = role(2L, SystemRole.ORG_ADMIN.authority());
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        User target = user(42L, "actor@example.com", currentRole, true);

        when(userRepository.findById(42L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(superAdminRole));

        assertThatThrownBy(() -> roleService.assignRoleToUser(
                new AssignRoleRequest(42L, 1L),
                "actor@example.com"
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot assign SUPER_ADMIN to yourself");

        verify(userRepository, never()).save(target);
    }

    @Test
    void lastActiveSuperAdminCannotBeDowngraded() {
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        Role moderatorRole = role(3L, SystemRole.MODERATOR.authority());
        User target = user(42L, "last-admin@example.com", superAdminRole, true);

        when(userRepository.findById(42L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(moderatorRole));
        when(userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(1L);

        assertThatThrownBy(() -> roleService.assignRoleToUser(
                new AssignRoleRequest(42L, 3L),
                "another-admin@example.com"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last active SUPER_ADMIN");

        verify(userRepository, never()).save(target);
    }

    @Test
    void systemRoleCannotBeDeleted() {
        Role role = role(1L, SystemRole.SUPER_ADMIN.authority());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> roleService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("System role cannot be deleted");

        verify(roleRepository, never()).delete(role);
        verifyNoInteractions(userRepository);
    }

    @Test
    void assignedCustomRoleCannotBeDeleted() {
        Role role = role(8L, "ROLE_CATALOG_EDITOR");
        when(roleRepository.findById(8L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole_Id(8L)).thenReturn(true);

        assertThatThrownBy(() -> roleService.delete(8L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assigned to users");

        verify(roleRepository, never()).delete(role);
    }

    @Test
    void unassignedCustomRoleCanBeDeleted() {
        Role role = role(8L, "ROLE_CATALOG_EDITOR");
        when(roleRepository.findById(8L)).thenReturn(Optional.of(role));
        when(userRepository.existsByRole_Id(8L)).thenReturn(false);

        roleService.delete(8L);

        verify(roleRepository).delete(role);
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
