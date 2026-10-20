package adliya.uz.task1.config.security;

import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private static final String SECRET = "test-jwt-secret-key-32-bytes!!!!";

    @Test
    void generatesAndValidatesHmacTokenWithExistingClaims() {
        JwtService service = service();
        Permission view = Permission.builder().code("USERS_VIEW").build();
        Permission edit = Permission.builder().code("USERS_EDIT").build();
        User user = User.builder().id(42L).email("admin@example.com")
                .role(Role.builder()
                        .name("ROLE_SUPER_ADMIN")
                        .permissions(Set.of(view, edit))
                        .build())
                .organizations(Set.of(Organization.builder().id(10L).build(), Organization.builder().id(20L).build()))
                .mustChangePassword(true).build();

        String token = service.generateToken(user);
        var parsed = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token);

        assertThat(parsed.getPayload().get("userId", Long.class)).isEqualTo(42L);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(parsed.getPayload().getSubject()).isEqualTo("admin@example.com");
        assertThat(parsed.getPayload().get("role", String.class)).isEqualTo("ROLE_SUPER_ADMIN");
        assertThat(parsed.getPayload().get("organizationIds", java.util.List.class)).containsExactlyInAnyOrder(10, 20);
        assertThat(parsed.getPayload().get("permissions", java.util.List.class))
                .containsExactlyInAnyOrder("USERS_VIEW", "USERS_EDIT");
        assertThat(parsed.getPayload().get("mustChangePassword", Boolean.class)).isTrue();
        assertThat(service.extractUsername(token)).isEqualTo("admin@example.com");
    }

    @Test
    void tokensIssuedBeforeThePasswordChangedAreRejectedWhileNewerOnesStayValid() throws Exception {
        JwtService service = service();
        User user = User.builder().id(7L).email("staff@example.com")
                .role(Role.builder().name("ROLE_MODERATOR").permissions(Set.of()).build())
                .organizations(Set.of()).build();
        String before = service.generateToken(user);
        var issuedAt = service.extractClaim(before, io.jsonwebtoken.Claims::getIssuedAt).toInstant();

        var unchanged = new CustomUserPrincipal("staff@example.com", "hash", java.util.List.of(), true, false, null);
        assertThat(service.isTokenValid(before, unchanged)).isTrue();

        var changedLater = new CustomUserPrincipal("staff@example.com", "hash", java.util.List.of(), true, false, issuedAt.plusSeconds(5));
        assertThat(service.isTokenValid(before, changedLater)).isFalse();

        var changedSameSecond = new CustomUserPrincipal("staff@example.com", "hash", java.util.List.of(), true, false, issuedAt.plusMillis(300));
        assertThat(service.isTokenValid(before, changedSameSecond)).isTrue();

        var changedEarlier = new CustomUserPrincipal("staff@example.com", "hash", java.util.List.of(), true, false, issuedAt.minusSeconds(5));
        assertThat(service.isTokenValid(before, changedEarlier)).isTrue();
    }

    @Test
    void sessionIdAndValidityAreReadFromOneParsedToken() {
        JwtService service = service();
        User user = User.builder().id(7L).email("staff@example.com")
                .role(Role.builder().name("ROLE_MODERATOR").permissions(Set.of()).build())
                .organizations(Set.of()).build();
        var claims = service.extractAllClaims(service.generateToken(user, "session-7"));

        assertThat(JwtService.sessionId(claims)).isEqualTo("session-7");
        assertThat(JwtService.sessionId(service.extractAllClaims(service.generateToken(user)))).isNull();
        var principal = new CustomUserPrincipal("staff@example.com", "hash", java.util.List.of(), true, false, null);
        assertThat(service.isTokenValid(claims, principal)).isTrue();
        var someoneElse = new CustomUserPrincipal("other@example.com", "hash", java.util.List.of(), true, false, null);
        assertThat(service.isTokenValid(claims, someoneElse)).isFalse();
    }

    private static JwtService service() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpiration(60_000L);
        properties.setRefreshExpiration(120_000L);
        JwtService service = new JwtService(properties);
        service.init();
        return service;
    }
}
