package adliya.uz.task1.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mfa")
public class MfaProperties implements ClientRateLimitProperties {
    /**
     * Master switch (MFA_ENABLED). Off by default: no second factor is required or offered and no encryption key is
     * needed. Switch it on for production and supply the same MFA_ENCRYPTION_KEY that encrypted existing enrollments.
     */
    private boolean enabled = false;
    /** Base64-encoded 32-byte key that encrypts TOTP secrets at rest (MFA_ENCRYPTION_KEY). Required when enabled. */
    private String encryptionKey = "";
    /** Roles that must complete a second factor before receiving a full session. */
    private List<String> requiredRoles = List.of("ROLE_SUPER_ADMIN", "ROLE_ORG_ADMIN");
    /**
     * Until this moment, users of a required role who are not enrolled yet may still sign in with the password alone and
     * are reminded to enroll. Empty means enrollment is required at the next sign-in.
     */
    private Instant enrollmentDeadline;
    /** Name shown in authenticator apps. */
    private String issuer = "ReestrGov";
    /** Lifetime of the single-use challenge issued after the password step. */
    private Duration challengeTtl = Duration.ofMinutes(5);
    /** Wrong codes accepted per challenge before it is discarded. */
    private int maxAttemptsPerChallenge = 5;
    private int recoveryCodeCount = 10;
    /** Accepted time-step drift on either side of the current step. */
    private int window = 1;
    private String clientAddressHeader = "X-Real-IP";
    private int perClientLimit = 10;
    private Duration perClientWindow = Duration.ofMinutes(15);
    private int globalLimit = 1_000;
    private Duration globalWindow = Duration.ofHours(1);
    private int maxTrackedClients = 10_000;
}
