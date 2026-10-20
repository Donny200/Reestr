package adliya.uz.task1.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;

@RequiredArgsConstructor
abstract class AbstractSuperAdminInitializer implements CommandLineRunner {

    private final SuperAdminProvisioner provisioner;
    private final Environment environment;

    @Override
    public void run(String... args) {
        if (provisioner.superAdminExists()) {
            return;
        }
        String email = requiredEnvironmentValue(emailVariable());
        String password = requiredEnvironmentValue(passwordVariable());
        provisioner.provisionIfMissing(email, password).ifPresent(this::onCreated);
    }

    protected abstract String emailVariable();

    protected abstract String passwordVariable();

    protected abstract String missingVariableMessage(String variableName);

    protected abstract void onCreated(String email);

    private String requiredEnvironmentValue(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(missingVariableMessage(name));
        }
        return value;
    }
}
