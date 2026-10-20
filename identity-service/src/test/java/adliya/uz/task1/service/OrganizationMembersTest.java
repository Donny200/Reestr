package adliya.uz.task1.service;

import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** The member list behind responsible-staff assignment is scoped to the caller's own organizations. */
@ExtendWith(MockitoExtension.class)
class OrganizationMembersTest {

    @Mock OrganizationRepository organizations;
    @Mock OrganizationTranslationStateService translations;
    @Mock UserService userService;
    @InjectMocks OrganizationService service;

    @Test
    void onlyEnabledMembersAreListedAndOnlyForOwnOrganizationsUnlessGlobal() {
        Organization mine = Organization.builder().id(10L).name("Mine").enabled(true).build();
        Organization other = Organization.builder().id(20L).name("Other").enabled(true).build();
        User active = user(2L, "ROLE_MODERATOR", true, mine);
        User disabled = user(3L, "ROLE_MODERATOR", false, mine);
        User orgAdmin = user(1L, "ROLE_ORG_ADMIN", true, mine);
        mine.getMembers().addAll(Set.of(active, disabled, orgAdmin));
        other.getMembers().add(user(4L, "ROLE_MODERATOR", true, other));
        when(organizations.findById(10L)).thenReturn(Optional.of(mine));
        when(organizations.findById(20L)).thenReturn(Optional.of(other));

        when(userService.getCurrentUser()).thenReturn(orgAdmin);
        assertThat(service.activeMembers(10L)).extracting(User::getId).containsExactlyInAnyOrder(1L, 2L);
        assertThatThrownBy(() -> service.activeMembers(20L)).isInstanceOf(AccessDeniedException.class);

        when(userService.getCurrentUser()).thenReturn(user(9L, "ROLE_SUPER_ADMIN", true));
        assertThat(service.activeMembers(20L)).extracting(User::getId).containsExactly(4L);
    }

    private static User user(long id, String role, boolean enabled, Organization... organizations) {
        Role entity = Role.builder().name(role).permissions(Set.of(Permission.builder().code("ORGANIZATIONS_VIEW").build())).build();
        User user = User.builder().id(id).firstName("F" + id).lastName("L" + id).email("u" + id + "@example.test").password("x")
                .role(entity).enabled(enabled).build();
        user.getOrganizations().addAll(Set.of(organizations));
        return user;
    }
}
