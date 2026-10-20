package adliya.uz.task1.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev & !test")
@Order(100)
@Slf4j
public class BootstrapSuperAdminInitializer extends AbstractSuperAdminInitializer {

    static final String EMAIL_ENV = "BOOTSTRAP_SUPER_ADMIN_EMAIL";
    static final String PASSWORD_ENV = "BOOTSTRAP_SUPER_ADMIN_PASSWORD";

    public BootstrapSuperAdminInitializer(SuperAdminProvisioner provisioner, Environment environment) {
        super(provisioner, environment);
    }

    @Override
    protected String emailVariable() {
        return EMAIL_ENV;
    }

    @Override
    protected String passwordVariable() {
        return PASSWORD_ENV;
    }

    @Override
    protected String missingVariableMessage(String variableName) {
        return "No SUPER_ADMIN exists. Set environment variable " + variableName + " before startup";
    }

    @Override
    protected void onCreated(String email) {
        log.warn("Initial SUPER_ADMIN account created for {}. A password change is required.", email);
    }
}
