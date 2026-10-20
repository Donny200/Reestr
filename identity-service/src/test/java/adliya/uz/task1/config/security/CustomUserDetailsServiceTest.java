package adliya.uz.task1.config.security;

import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void principalCarriesMandatoryPasswordChangeFlag() {
        Permission permission = Permission.builder().code("ROLES_VIEW").build();
        Role role = Role.builder()
                .name("ROLE_SUPER_ADMIN")
                .permissions(Set.of(permission))
                .build();
        User user = User.builder()
                .email("root@example.com")
                .password("hash")
                .enabled(true)
                .mustChangePassword(true)
                .role(role)
                .build();
        when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.of(user));

        CustomUserPrincipal principal = (CustomUserPrincipal) new CustomUserDetailsService(userRepository)
                .loadUserByUsername("root@example.com");

        assertThat(principal.mustChangePassword()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLES_VIEW");
    }
}
