package adliya.uz.task1.dto;

import adliya.uz.task1.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest {
    @Test void exposesActualAuthoritiesAndOnlyAssignedOrganizationSummariesWithoutSecrets() throws Exception {
        var user = User.builder().id(7L).email("editor@example.test").password("never-expose")
                .role(Role.builder().name("ROLE_MODERATOR").permissions(Set.of(
                        Permission.builder().code("FUNCTIONS_VIEW").build(),
                        Permission.builder().code("FUNCTIONS_REVIEW").build())).build())
                .organizations(Set.of(Organization.builder().id(3L).name("Archive").build())).build();
        var response = UserResponse.from(user);
        assertThat(response.getRole()).isEqualTo("ROLE_MODERATOR");
        assertThat(response.getPermissions()).containsExactly("FUNCTIONS_REVIEW", "FUNCTIONS_VIEW");
        assertThat(response.getOrganizations()).containsExactly(new UserResponse.OrganizationSummary(3L, "Archive"));
        assertThat(response.getOrganizationIds()).containsExactly(3L);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
                .doesNotContain("password", "never-expose");
    }
    @Test void doesNotInventPermissionsForAnEmptyRole() {
        var user = User.builder().role(Role.builder().name("ROLE_USER").build()).build();
        assertThat(UserResponse.from(user).getPermissions()).isEmpty();
        assertThat(UserResponse.from(user).getOrganizations()).isEmpty();
    }
}
