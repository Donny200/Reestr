package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.CreateUserRequest;
import adliya.uz.task1.dto.UpdateUserRequest;
import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.EmailAlreadyExistsException;
import adliya.uz.task1.repository.OrganizationRepository;
import adliya.uz.task1.repository.RefreshTokenRepository;
import adliya.uz.task1.repository.RoleRepository;
import adliya.uz.task1.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createResolvesManagedRelationsHashesPasswordAndForcesEnabled() {
        User caller = user(1L, "admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true);
        authenticate(caller, "USERS_CREATE");

        Role role = role(2L, SystemRole.MODERATOR.authority());
        Organization organization = Organization.builder()
                .id(10L)
                .name("Active organization")
                .enabled(true)
                .build();
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("  Ada  ")
                .lastName("  Lovelace ")
                .email(" ada@example.com ")
                .password("plain-password")
                .phone(" +998901234567 ")
                .roleId(role.getId())
                .organizationIds(Set.of(organization.getId()))
                .build();

        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(passwordEncoder.encode("plain-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        UserResponse response = userService.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getLastName()).isEqualTo("Lovelace");
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getPassword()).isEqualTo("bcrypt-hash").isNotEqualTo("plain-password");
        assertThat(saved.getRole()).isSameAs(role);
        assertThat(saved.getOrganizations()).containsExactly(organization);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(response.getRole()).isEqualTo(SystemRole.MODERATOR.authority());
        assertThat(response.getOrganizationIds()).containsExactly(10L);
        verify(passwordEncoder).encode("plain-password");
    }

    @Test
    void createRejectsInactiveOrganization() {
        authenticate(user(1L, "admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true), "USERS_CREATE");
        Role role = role(2L, SystemRole.MODERATOR.authority());
        Organization inactive = Organization.builder().id(10L).name("Inactive").enabled(false).build();
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .password("plain-password")
                .roleId(role.getId())
                .organizationIds(Set.of(inactive.getId()))
                .build();

        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(organizationRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateRejectsAnEmailAlreadyUsedByAnotherUser() {
        authenticate(user(1L, "admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true), "USERS_EDIT");
        User target = user(2L, "old@example.com", SystemRole.MODERATOR.authority(), true);
        UpdateUserRequest request = UpdateUserRequest.builder()
                .email("used@example.com")
                .build();

        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.existsByEmail("used@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(target.getId(), request))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(target);
    }

    @Test
    void updateHashesANewPassword() {
        authenticate(user(1L, "admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true), "USERS_EDIT");
        User target = user(2L, "user@example.com", SystemRole.MODERATOR.authority(), true);
        UpdateUserRequest request = UpdateUserRequest.builder()
                .password("new-password")
                .build();

        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("new-password")).thenReturn("new-bcrypt-hash");
        when(userRepository.save(target)).thenReturn(target);

        userService.update(target.getId(), request);

        assertThat(target.getPassword()).isEqualTo("new-bcrypt-hash");
        verify(passwordEncoder).encode("new-password");
    }

    @Test
    void deactivateRejectsTheCurrentUser() {
        User caller = user(1L, "admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true);
        authenticate(caller, "USERS_DEACTIVATE");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> userService.deactivate(caller.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("own account");
        verify(userRepository, never()).save(caller);
    }

    @Test
    void deactivateRejectsTheLastEnabledSuperAdmin() {
        User caller = user(1L, "admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true);
        User target = user(2L, "other-admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true);
        authenticate(caller, "USERS_DEACTIVATE");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivate(target.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last enabled SUPER_ADMIN");
        assertThat(target.getEnabled()).isTrue();
        verify(userRepository, never()).save(target);
    }

    @Test
    void deactivateIsSoftAndKeepsTheUserRecord() {
        User caller = user(1L, "admin@reestr.uz", SystemRole.SUPER_ADMIN.authority(), true);
        User target = user(2L, "moderator@reestr.uz", SystemRole.MODERATOR.authority(), true);
        authenticate(caller, "USERS_DEACTIVATE");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        userService.deactivate(target.getId());

        assertThat(target.getEnabled()).isFalse();
        verify(userRepository).save(target);
        verify(refreshTokenRepository).revokeAllByUserId(eq(target.getId()), any(), eq(SessionService.REASON_DEACTIVATED));
        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).deleteById(target.getId());
    }

    @Test
    void legacyOperationsRequireAnEnabledSuperAdminInsideTheService() {
        User ordinaryUser = user(1L, "user@reestr.uz", SystemRole.MODERATOR.authority(), true);
        authenticate(ordinaryUser);

        assertThatThrownBy(userService::getAllForLegacyApi)
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).findAll();
    }

    @Test
    void passwordHasAJsonIgnoreSafetyLayer() throws Exception {
        User user = user(1L, "user@reestr.uz", SystemRole.MODERATOR.authority(), true);
        user.setPassword("must-not-appear");
        user.setCreatedAt(null);

        String json = new ObjectMapper().writeValueAsString(user);

        assertThat(json).doesNotContain("password", "must-not-appear");
    }

    private void authenticate(User caller, String... permissionCodes) {
        caller.getRole().setPermissions(java.util.Arrays.stream(permissionCodes)
                .map(code -> Permission.builder().code(code).build())
                .collect(java.util.stream.Collectors.toSet()));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                caller.getEmail(),
                "unused",
                List.of(new SimpleGrantedAuthority(caller.getRole().getName()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
    }

    private User user(Long id, String email, String roleName, boolean enabled) {
        return User.builder()
                .id(id)
                .firstName("First")
                .lastName("Last")
                .email(email)
                .password("existing-hash")
                .role(role(id + 100, roleName))
                .enabled(enabled)
                .build();
    }

    private Role role(Long id, String name) {
        return Role.builder()
                .id(id)
                .name(name)
                .build();
    }
}
