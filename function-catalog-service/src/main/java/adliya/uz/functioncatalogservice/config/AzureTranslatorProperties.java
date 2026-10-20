package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "azure.translator")
@Getter
@Setter
public class AzureTranslatorProperties {
    private String endpoint;
    private String key;
    private String region;
}
