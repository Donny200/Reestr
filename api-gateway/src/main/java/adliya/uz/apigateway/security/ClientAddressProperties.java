package adliya.uz.apigateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Which callers may set the visitor-address headers that the backend rate limiters rely on (gateway.client-address.*). */
@Component
@ConfigurationProperties(prefix = "gateway.client-address")
public class ClientAddressProperties {
    /** Header the backends read (identity, catalog: X-Real-IP). */
    private String header = "X-Real-IP";
    /** Reverse proxies (IP or CIDR) whose address headers are kept. Everyone else gets the header overwritten. */
    private List<String> trustedProxies = new ArrayList<>();

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }
}
