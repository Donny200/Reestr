package adliya.uz.task1.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BootstrapSuperAdminInitializerTest {

    @Mock
    private SuperAdminProvisioner provisioner;

    @Test
    void doesNotRequireBootstrapSecretsAfterSuperAdminExists() {
        when(provisioner.superAdminExists()).thenReturn(true);
        BootstrapSuperAdminInitializer initializer = new BootstrapSuperAdminInitializer(
                provisioner,
                new MockEnvironment()
        );

        initializer.run();

        verify(provisioner, never()).provisionIfMissing(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void failsClosedWhenFirstStartCredentialsAreMissing() {
        when(provisioner.superAdminExists()).thenReturn(false);
        BootstrapSuperAdminInitializer initializer = new BootstrapSuperAdminInitializer(
                provisioner,
                new MockEnvironment()
        );

        assertThatThrownBy(initializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BootstrapSuperAdminInitializer.EMAIL_ENV);
    }

    @Test
    void readsOneTimeCredentialsFromEnvironmentWithoutDefaults(CapturedOutput output) {
        String password = "bootstrap-secret-2026";
        MockEnvironment environment = new MockEnvironment()
                .withProperty(BootstrapSuperAdminInitializer.EMAIL_ENV, "root@example.com")
                .withProperty(BootstrapSuperAdminInitializer.PASSWORD_ENV, password);
        when(provisioner.superAdminExists()).thenReturn(false);
        when(provisioner.provisionIfMissing("root@example.com", password))
                .thenReturn(Optional.of("root@example.com"));
        BootstrapSuperAdminInitializer initializer = new BootstrapSuperAdminInitializer(provisioner, environment);

        initializer.run();

        verify(provisioner).provisionIfMissing("root@example.com", password);
        assertThat(output).doesNotContain(password);
    }

    @Test
    void productionAndDemoInitializersHaveMutuallyExclusiveProfiles() {
        Profile productionProfile = BootstrapSuperAdminInitializer.class.getAnnotation(Profile.class);
        Profile demoProfile = DemoUserDataInitializer.class.getAnnotation(Profile.class);
        ConditionalOnProperty demoOptIn = DemoUserDataInitializer.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(productionProfile.value()).containsExactly("!dev & !test");
        assertThat(demoProfile.value()).containsExactly("!prod & (dev | test)");
        assertThat(demoOptIn.name()).containsExactly("app.demo-users.enabled");
        assertThat(demoOptIn.havingValue()).isEqualTo("true");
        assertThat(demoOptIn.matchIfMissing()).isFalse();
    }
}
