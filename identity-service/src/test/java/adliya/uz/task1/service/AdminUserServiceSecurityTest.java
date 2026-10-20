package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.PromoteToOrgAdminRequest;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private RoleService roleService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void promoteToOrgAdminRejectsDowngradeOfLastActiveSuperAdmin() {
        User target = user(1L, SystemRole.SUPER_ADMIN.authority(), true);
        PromoteToOrgAdminRequest request = PromoteToOrgAdminRequest.builder()
                .userId(1L)
                .organizationId(20L)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.promoteToOrgAdmin(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last enabled SUPER_ADMIN");

        assertThat(target.getRole().getName()).isEqualTo(SystemRole.SUPER_ADMIN.authority());
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(organizationService, roleService);
    }

    @Test
    void promotionCandidatesExcludeDisabledAndPrivilegedUsers() {
        User eligible = user(1L, "ROLE_USER", true);
        User disabled = user(2L, "ROLE_USER", false);
        User superAdmin = user(3L, SystemRole.SUPER_ADMIN.authority(), true);
        User orgAdmin = user(4L, SystemRole.ORG_ADMIN.authority(), true);
        User moderator = user(5L, SystemRole.MODERATOR.authority(), true);
        User withoutRole = user(6L, null, true);
        when(userRepository.findAll()).thenReturn(List.of(
                eligible,
                disabled,
                superAdmin,
                orgAdmin,
                moderator,
                withoutRole
        ));

        List<User> candidates = adminUserService.getPromotionCandidates();

        assertThat(candidates).containsExactly(eligible, moderator);
    }

    private User user(Long id, String roleName, boolean enabled) {
        Role role = roleName == null ? null : Role.builder().name(roleName).build();
        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("User")
                .email("user" + id + "@reestr.uz")
                .password("encoded")
                .role(role)
                .enabled(enabled)
                .build();
    }
}
