package adliya.uz.task1.config;

import java.time.Duration;

/** Per-client and global fixed-window limits shared by the login, MFA and password-reset settings. */
public interface ClientRateLimitProperties {
    String getClientAddressHeader();
    int getPerClientLimit();
    Duration getPerClientWindow();
    int getGlobalLimit();
    Duration getGlobalWindow();
    int getMaxTrackedClients();
}
