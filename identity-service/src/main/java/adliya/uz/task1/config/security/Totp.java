package adliya.uz.task1.config.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.OptionalLong;

/** RFC 6238 time-based one-time passwords (SHA-1, 6 digits, 30-second steps), compatible with common authenticator apps. */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** New random secret, base32 encoded (what authenticator apps accept as the manual key). */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long step(long epochSeconds) {
        return Math.floorDiv(epochSeconds, PERIOD_SECONDS);
    }

    public static String code(String base32Secret, long step) {
        byte[] key = decodeBase32(base32Secret);
        byte[] message = new byte[8];
        long value = step;
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) (value & 0xff);
            value >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA1 is unavailable", exception);
        }
    }

    /** The matching step within ±window of now, if the code is valid; constant-time comparison per candidate. */
    public static OptionalLong matchingStep(String base32Secret, String code, long epochSeconds, int window) {
        String normalized = code == null ? "" : code.replaceAll("[\\s-]", "");
        if (normalized.length() != DIGITS || !normalized.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        long current = step(epochSeconds);
        for (long candidate = current - window; candidate <= current + window; candidate++) {
            if (constantTimeEquals(code(base32Secret, candidate), normalized)) {
                return OptionalLong.of(candidate);
            }
        }
        return OptionalLong.empty();
    }

    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    static byte[] decodeBase32(String value) {
        String cleaned = value.replace("=", "").replace(" ", "").toUpperCase();
        byte[] out = new byte[cleaned.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : cleaned.toCharArray()) {
            int digit = BASE32.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }
}
