package adliya.uz.apigateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Backends key their rate limits on {@code X-Real-IP}. Only a trusted reverse proxy (nginx in front of the SPA) may
 * supply that header; for every other caller the gateway replaces it with the actual remote address and drops
 * {@code X-Forwarded-For}, so a client that reaches the gateway directly cannot rotate addresses.
 */
@Component
public class ClientAddressFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ClientAddressFilter.class);

    private final ClientAddressProperties properties;
    private final List<Cidr> trusted;

    public ClientAddressFilter(ClientAddressProperties properties) {
        this.properties = properties;
        this.trusted = properties.getTrustedProxies().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(Cidr::parse)
                .toList();
        if (trusted.isEmpty()) {
            log.info("No trusted proxies configured: {} is always set to the remote address", properties.getHeader());
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        InetAddress address = remote == null ? null : remote.getAddress();
        if (address != null && trusted.stream().anyMatch(cidr -> cidr.contains(address))) {
            return chain.filter(exchange);
        }
        String remoteIp = address == null ? "unknown" : address.getHostAddress();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(properties.getHeader(), remoteIp);
                    headers.remove("X-Forwarded-For");
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /** Minimal IPv4/IPv6 CIDR matcher; a bare address means a /32 or /128. */
    record Cidr(byte[] network, int prefixLength) {
        static Cidr parse(String value) {
            String[] parts = value.trim().split("/");
            try {
                byte[] network = InetAddress.getByName(parts[0]).getAddress();
                int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : network.length * 8;
                if (prefix < 0 || prefix > network.length * 8) {
                    throw new IllegalArgumentException("Invalid prefix length in trusted proxy " + value);
                }
                return new Cidr(network, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy " + value, exception);
            }
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remaining = prefixLength % 8;
            if (remaining == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remaining)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
