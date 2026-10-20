package adliya.uz.functioncatalogservice.config;

import java.time.Duration;

public interface ClientLimitProperties {
    int getPerClientLimit();
    Duration getPerClientWindow();
    int getGlobalLimit();
    Duration getGlobalWindow();
    int getMaxTrackedClients();
}
