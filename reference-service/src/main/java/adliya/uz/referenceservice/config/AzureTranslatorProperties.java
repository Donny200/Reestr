package adliya.uz.referenceservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Azure Translator credentials (the same AZURE_TRANSLATOR_* variables as function-catalog-service). Read only from the
 * environment or the untracked .env; never returned by an endpoint, written to the database or logged.
 */
@Component
@ConfigurationProperties(prefix = "azure.translator")
@Getter
@Setter
public class AzureTranslatorProperties {
    private String endpoint;
    private String key;
    private String region;

    public boolean configured() {
        return StringUtils.hasText(endpoint) && StringUtils.hasText(key) && StringUtils.hasText(region);
    }

    @Override
    public String toString() {
        return "AzureTranslatorProperties[endpoint=" + endpoint + ", region=" + region + ", key=" + (StringUtils.hasText(key) ? "***" : "") + "]";
    }
}
