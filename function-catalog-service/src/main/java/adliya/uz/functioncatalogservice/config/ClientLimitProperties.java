package adliya.uz.functioncatalogservice.config;

import java.time.Duration;

/** Per-visitor-address and overall request limits of one public endpoint group. */
public interface ClientLimitProperties {
    int getPerClientLimit();
    Duration getPerClientWindow();
    int getGlobalLimit();
    Duration getGlobalWindow();
    int getMaxTrackedClients();
}
