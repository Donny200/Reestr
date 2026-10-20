package adliya.uz.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressFilterTest {

    @Test
    void untrustedCallerCannotSpoofTheVisitorAddress() {
        ClientAddressProperties properties = new ClientAddressProperties();
        properties.setTrustedProxies(List.of("172.28.0.10/32", "10.0.0.0/8"));
        ClientAddressFilter filter = new ClientAddressFilter(properties);

        ServerWebExchange forwarded = run(filter, "172.28.0.1", "198.51.100.7");
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-Real-IP")).isEqualTo("172.28.0.1");
        assertThat(forwarded.getRequest().getHeaders().containsKey("X-Forwarded-For")).isFalse();

        ServerWebExchange trusted = run(filter, "172.28.0.10", "198.51.100.7");
        assertThat(trusted.getRequest().getHeaders().getFirst("X-Real-IP")).isEqualTo("198.51.100.7");
        assertThat(trusted.getRequest().getHeaders().getFirst("X-Forwarded-For")).isEqualTo("198.51.100.7");

        ServerWebExchange privateRange = run(filter, "10.20.30.40", "203.0.113.5");
        assertThat(privateRange.getRequest().getHeaders().getFirst("X-Real-IP")).isEqualTo("203.0.113.5");
    }

    @Test
    void withoutTrustedProxiesTheHeaderAlwaysReflectsTheRemoteAddress() {
        ClientAddressFilter filter = new ClientAddressFilter(new ClientAddressProperties());
        ServerWebExchange result = run(filter, "127.0.0.1", "198.51.100.7");
        assertThat(result.getRequest().getHeaders().getFirst("X-Real-IP")).isEqualTo("127.0.0.1");
    }

    @Test
    void cidrParsingCoversPrefixesAndBareAddresses() {
        assertThat(ClientAddressFilter.Cidr.parse("192.168.1.0/24").contains(address("192.168.1.200"))).isTrue();
        assertThat(ClientAddressFilter.Cidr.parse("192.168.1.0/24").contains(address("192.168.2.1"))).isFalse();
        assertThat(ClientAddressFilter.Cidr.parse("192.168.1.5").contains(address("192.168.1.5"))).isTrue();
        assertThat(ClientAddressFilter.Cidr.parse("192.168.1.5").contains(address("192.168.1.6"))).isFalse();
        assertThat(ClientAddressFilter.Cidr.parse("fd00::/8").contains(address("fd12::1"))).isTrue();
        assertThat(ClientAddressFilter.Cidr.parse("fd00::/8").contains(address("192.168.1.5"))).isFalse();
    }

    private static ServerWebExchange run(ClientAddressFilter filter, String remote, String claimed) {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress(remote, 44321))
                .header("X-Real-IP", claimed)
                .header("X-Forwarded-For", claimed)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = next -> { seen.set(next); return Mono.empty(); };
        filter.filter(exchange, chain).block();
        return seen.get();
    }

    private static java.net.InetAddress address(String value) {
        try {
            return java.net.InetAddress.getByName(value);
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
