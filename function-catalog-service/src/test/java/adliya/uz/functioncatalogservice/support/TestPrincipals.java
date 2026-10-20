package adliya.uz.functioncatalogservice.support;

import adliya.uz.functioncatalogservice.security.JwtPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public final class TestPrincipals {

    public static final List<String> EDITOR = List.of("FUNCTIONS_CREATE", "FUNCTIONS_EDIT", "FUNCTIONS_MANAGE_REQUIREMENTS",
            "FUNCTIONS_DEACTIVATE", "FUNCTIONS_SUBMIT_REVIEW", "FUNCTIONS_REVIEW", "FUNCTIONS_PUBLISH", "FUNCTIONS_REACTIVATE",
            "FUNCTIONS_TRANSLATIONS_EDIT", "FUNCTION_CATEGORIES_MANAGE", "AUDIT_VIEW", "FUNCTIONS_VIEW");

    private TestPrincipals() {}

    public static UsernamePasswordAuthenticationToken superAdmin(String... permissions) {
        return token("admin@example.test", "ROLE_SUPER_ADMIN", List.of(), List.of(permissions), 1L);
    }

    public static UsernamePasswordAuthenticationToken staff(long userId, List<Long> organizationIds, String... permissions) {
        return token("staff" + userId + "@example.test", "ROLE_ORG_ADMIN", organizationIds, List.of(permissions), userId);
    }

    public static UsernamePasswordAuthenticationToken token(String email, String role, List<Long> organizationIds,
                                                            List<String> permissions, Long userId) {
        var principal = new JwtPrincipal(email, role, organizationIds, permissions, userId);
        return new UsernamePasswordAuthenticationToken(principal, null, permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }

    public static void login(UsernamePasswordAuthenticationToken authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static String[] with(List<String> base, String... extra) {
        return Stream.concat(base.stream(), Arrays.stream(extra)).toArray(String[]::new);
    }
}
