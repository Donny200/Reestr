package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.PromoteToModeratorRequest;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModeratorServiceSecurityTest {

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
    private ModeratorService moderatorService;

    @Test
    void promoteRejectsDowngradeOfLastActiveSuperAdmin() {
        User target = user(1L, SystemRole.SUPER_ADMIN.authority(), true, organization(10L));
        User current = user(2L, SystemRole.SUPER_ADMIN.authority(), true);
        PromoteToModeratorRequest request = PromoteToModeratorRequest.builder()
                .userId(1L)
                .organizationIds(Set.of(10L))
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(userService.getCurrentUser()).thenReturn(current);
        when(userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(1L);

        assertThatThrownBy(() -> moderatorService.promote(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last enabled SUPER_ADMIN");

        assertThat(target.getRole().getName()).isEqualTo(SystemRole.SUPER_ADMIN.authority());
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(organizationService, roleService);
    }

    @Test
    void orgAdminCannotPromoteCandidateOutsideOrganizationScope() {
        Organization ownOrganization = organization(10L);
        Organization foreignOrganization = organization(20L);
        User current = user(1L, SystemRole.ORG_ADMIN.authority(), true, ownOrganization);
        User foreignCandidate = user(2L, "ROLE_USER", true, foreignOrganization);
        PromoteToModeratorRequest request = PromoteToModeratorRequest.builder()
                .userId(2L)
                .organizationIds(Set.of(10L))
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(foreignCandidate));
        when(userService.getCurrentUser()).thenReturn(current);

        assertThatThrownBy(() -> moderatorService.promote(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("organization scope");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(organizationService, roleService);
    }

    @Test
    void orgAdminPromotionCandidatesAreLimitedToOwnOrganizationScope() {
        Organization ownOrganization = organization(10L);
        Organization foreignOrganization = organization(20L);
        User current = user(100L, SystemRole.ORG_ADMIN.authority(), true, ownOrganization);
        User ownCandidate = user(1L, "ROLE_USER", true, ownOrganization);
        User sharedCandidate = user(2L, "ROLE_USER", true, ownOrganization, foreignOrganization);
        User foreignCandidate = user(3L, "ROLE_USER", true, foreignOrganization);
        User ownModerator = user(4L, SystemRole.MODERATOR.authority(), true, ownOrganization);
        User ownOrgAdmin = user(5L, SystemRole.ORG_ADMIN.authority(), true, ownOrganization);
        User ownSuperAdmin = user(6L, SystemRole.SUPER_ADMIN.authority(), true, ownOrganization);
        User disabledOwnCandidate = user(7L, "ROLE_USER", false, ownOrganization);
        User withoutRole = user(8L, null, true, ownOrganization);
        when(userService.getCurrentUser()).thenReturn(current);
        when(userRepository.findAll()).thenReturn(List.of(
                ownCandidate,
                sharedCandidate,
                foreignCandidate,
                ownModerator,
                ownOrgAdmin,
                ownSuperAdmin,
                disabledOwnCandidate,
                withoutRole
        ));

        List<User> candidates = moderatorService.getPromotionCandidates();

        assertThat(candidates).containsExactly(ownCandidate);
    }

    private Organization organization(Long id) {
        return Organization.builder()
                .id(id)
                .name("Organization " + id)
                .enabled(true)
                .build();
    }

    private User user(Long id, String roleName, boolean enabled, Organization... organizations) {
        Role role = roleName == null ? null : Role.builder().name(roleName).build();
        User user = User.builder()
                .id(id)
                .firstName("Test")
                .lastName("User")
                .email("user" + id + "@reestr.uz")
                .password("encoded")
                .role(role)
                .enabled(enabled)
                .build();
        user.getOrganizations().addAll(Set.of(organizations));
        return user;
    }
}
