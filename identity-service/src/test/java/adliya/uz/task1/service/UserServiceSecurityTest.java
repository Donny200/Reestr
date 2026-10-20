package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.CreateUserRequest;
import adliya.uz.task1.dto.UpdateUserRequest;
import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.OrganizationRepository;
import adliya.uz.task1.repository.RoleRepository;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createHashesPasswordAndKeepsManagedFieldsOnServer() {
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        grant(superAdminRole, "USERS_CREATE");
        Role moderatorRole = role(3L, SystemRole.MODERATOR.authority());
        User actor = user(1L, "root@example.com", superAdminRole, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(moderatorRole));
        when(passwordEncoder.encode("PlainPassword123")).thenReturn("$2a$encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        UserResponse response = userService.create(CreateUserRequest.builder()
                .firstName(" New ")
                .lastName(" User ")
                .email("new@example.com")
                .password("PlainPassword123")
                .phone(null)
                .roleId(3L)
                .organizationIds(Set.of())
                .build());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        verify(passwordEncoder).encode("PlainPassword123");

        assertThat(savedUser.getValue().getPassword()).isEqualTo("$2a$encoded-password");
        assertThat(savedUser.getValue().getPassword()).isNotEqualTo("PlainPassword123");
        assertThat(savedUser.getValue().getEnabled()).isTrue();
        assertThat(savedUser.getValue().getRole()).isSameAs(moderatorRole);
        assertThat(response.getRole()).isEqualTo(SystemRole.MODERATOR.authority());
    }

    @Test
    void currentSuperAdminCannotDeactivateSelf() {
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        grant(superAdminRole, "USERS_DEACTIVATE");
        User actor = user(1L, "root@example.com", superAdminRole, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> userService.deactivate(actor.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot deactivate your own account");

        verify(userRepository, never()).save(actor);
    }

    @Test
    void lastEnabledSuperAdminCannotBeDeactivated() {
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        grant(superAdminRole, "USERS_DEACTIVATE");
        User actor = user(1L, "root@example.com", superAdminRole, true);
        User target = user(2L, "last-admin@example.com", superAdminRole, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivate(target.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last enabled SUPER_ADMIN");

        verify(userRepository, never()).save(target);
    }

    @Test
    void customRoleWithUsersViewCanListUsers() {
        Role viewerRole = role(4L, "ROLE_USER_VIEWER");
        grant(viewerRole, "USERS_VIEW");
        User actor = user(1L, "viewer@example.com", viewerRole, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findAll()).thenReturn(List.of());

        assertThat(userService.getAllForLegacyApi()).isEmpty();
        verify(userRepository).findAll();
    }

    @Test
    void userWithoutUsersViewCannotListUsers() {
        Role role = role(4L, "ROLE_USER_VIEWER");
        User actor = user(1L, "viewer@example.com", role, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));

        assertThatThrownBy(userService::getAllForLegacyApi)
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("USERS_VIEW");
        verify(userRepository, never()).findAll();
    }

    @Test
    void nonSuperAdminWithUsersEditCannotEditSuperAdmin() {
        Role editorRole = role(4L, "ROLE_USER_EDITOR");
        grant(editorRole, "USERS_EDIT");
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        User actor = user(1L, "editor@example.com", editorRole, true);
        User target = user(2L, "root@example.com", superAdminRole, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.update(
                target.getId(),
                UpdateUserRequest.builder().firstName("Changed").build()
        ))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("SUPER_ADMIN");
        verify(userRepository, never()).save(target);
    }

    @Test
    void nonSuperAdminWithUsersDeactivateCannotDeactivateSuperAdmin() {
        Role managerRole = role(4L, "ROLE_USER_MANAGER");
        grant(managerRole, "USERS_DEACTIVATE");
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        User actor = user(1L, "manager@example.com", managerRole, true);
        User target = user(2L, "root@example.com", superAdminRole, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.deactivate(target.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("SUPER_ADMIN");
        verify(userRepository, never()).save(target);
    }

    @Test
    void nonSuperAdminWithUsersCreateCannotCreateSuperAdmin() {
        Role creatorRole = role(4L, "ROLE_USER_CREATOR");
        grant(creatorRole, "USERS_CREATE");
        Role superAdminRole = role(1L, SystemRole.SUPER_ADMIN.authority());
        User actor = user(1L, "creator@example.com", creatorRole, true);
        authenticateAs(actor);

        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("new-root@example.com")).thenReturn(false);
        when(roleRepository.findById(superAdminRole.getId())).thenReturn(Optional.of(superAdminRole));

        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("New")
                .lastName("Root")
                .email("new-root@example.com")
                .password("StrongPassword123")
                .roleId(superAdminRole.getId())
                .organizationIds(Set.of())
                .build();

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("SUPER_ADMIN");
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    private void authenticateAs(User user) {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(user.getEmail(), null, user.getRole().getName());
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Role role(Long id, String name) {
        return Role.builder()
                .id(id)
                .name(name)
                .build();
    }

    private void grant(Role role, String permissionCode) {
        role.getPermissions().add(Permission.builder().code(permissionCode).build());
    }

    private User user(Long id, String email, Role role, boolean enabled) {
        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("Admin")
                .email(email)
                .password("encoded")
                .role(role)
                .enabled(enabled)
                .build();
    }
}
