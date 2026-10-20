package adliya.uz.functioncatalogservice.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Opaque secrets of anonymous support browsers (the HttpOnly guest cookie): 256 random bits, URL-safe Base64 without
 * padding (43 characters). Only the SHA-256 hash is persisted, so a database dump does not reveal working sessions.
 */
public final class HelpAccessTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private HelpAccessTokens() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean wellFormed(String token) {
        return token != null && FORMAT.matcher(token).matches();
    }

    public static String hash(String token) {
        return Sha256.hex(token.getBytes(StandardCharsets.US_ASCII));
    }
}
