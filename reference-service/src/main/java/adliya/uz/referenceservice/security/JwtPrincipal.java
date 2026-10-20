package adliya.uz.referenceservice.security;

import java.util.List;

public record JwtPrincipal(String email, String role, List<String> permissions) {
}
