package adliya.uz.referenceservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class SimpleJwtService {

    private final SecretKey secretKey;

    public SimpleJwtService(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parseClaims(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        if (!Boolean.FALSE.equals(claims.get("mustChangePassword", Boolean.class))) {
            throw new UnsupportedJwtException(
                    "JWT cannot authorize service access until the password has been changed"
            );
        }

        return claims;
    }
}
