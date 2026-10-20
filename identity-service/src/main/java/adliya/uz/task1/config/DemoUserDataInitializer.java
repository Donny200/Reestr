package adliya.uz.task1.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod & (dev | test)")
@ConditionalOnProperty(name = "app.demo-users.enabled", havingValue = "true")
@Order(100)
@Slf4j
public class DemoUserDataInitializer extends AbstractSuperAdminInitializer {

    static final String EMAIL_ENV = "DEMO_SUPER_ADMIN_EMAIL";
    static final String PASSWORD_ENV = "DEMO_SUPER_ADMIN_PASSWORD";

    public DemoUserDataInitializer(SuperAdminProvisioner provisioner, Environment environment) {
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
        return "Demo account initialization requires environment variable " + variableName;
    }

    @Override
    protected void onCreated(String email) {
        log.info("Opt-in demo SUPER_ADMIN account created for {}. A password change is required.", email);
    }
}
