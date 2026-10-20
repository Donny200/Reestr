package adliya.uz.task1.config;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.RoleRepository;
import adliya.uz.task1.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminProvisionerTest {

    private static final String RAW_PASSWORD = "one-time-password-2026";

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private SuperAdminProvisioner provisioner;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        provisioner = new SuperAdminProvisioner(roleRepository, userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenAnySuperAdminAlreadyExists() {
        when(userRepository.existsByRole_Name(SystemRole.SUPER_ADMIN.authority())).thenReturn(true);

        Optional<String> result = provisioner.provisionIfMissing(null, null);

        assertThat(result).isEmpty();
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(roleRepository);
    }

    @Test
    void createsEnabledSuperAdminWithBcryptAndMandatoryPasswordChange() {
        Role role = Role.builder().id(1L).name(SystemRole.SUPER_ADMIN.authority()).build();
        when(userRepository.existsByRole_Name(SystemRole.SUPER_ADMIN.authority())).thenReturn(false);
        when(userRepository.existsByEmail("root@example.com")).thenReturn(false);
        when(roleRepository.findByNameForUpdate(SystemRole.SUPER_ADMIN.authority())).thenReturn(Optional.of(role));

        Optional<String> result = provisioner.provisionIfMissing("  ROOT@Example.COM ", RAW_PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(result).contains("root@example.com");
        assertThat(saved.getEmail()).isEqualTo("root@example.com");
        assertThat(saved.getRole()).isSameAs(role);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getMustChangePassword()).isTrue();
        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(saved.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
    }

    @Test
    void rechecksForExistingSuperAdminAfterTakingRoleLock() {
        Role role = Role.builder().id(1L).name(SystemRole.SUPER_ADMIN.authority()).build();
        when(userRepository.existsByRole_Name(SystemRole.SUPER_ADMIN.authority()))
                .thenReturn(false, true);
        when(roleRepository.findByNameForUpdate(SystemRole.SUPER_ADMIN.authority())).thenReturn(Optional.of(role));

        Optional<String> result = provisioner.provisionIfMissing("root@example.com", RAW_PASSWORD);

        assertThat(result).isEmpty();
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWeakBootstrapPasswordWithoutIncludingItInTheError() {
        String weakPassword = "too-short";
        when(userRepository.existsByRole_Name(SystemRole.SUPER_ADMIN.authority())).thenReturn(false);

        assertThatThrownBy(() -> provisioner.provisionIfMissing("root@example.com", weakPassword))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 16 and 200")
                .hasMessageNotContaining(weakPassword);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsEmailAlreadyUsedByNonSuperAdmin() {
        Role role = Role.builder().id(1L).name(SystemRole.SUPER_ADMIN.authority()).build();
        when(userRepository.existsByRole_Name(SystemRole.SUPER_ADMIN.authority())).thenReturn(false);
        when(roleRepository.findByNameForUpdate(SystemRole.SUPER_ADMIN.authority())).thenReturn(Optional.of(role));
        when(userRepository.existsByEmail("root@example.com")).thenReturn(true);

        assertThatThrownBy(() -> provisioner.provisionIfMissing("root@example.com", RAW_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already assigned");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
