package adliya.uz.task1.config.security;

import adliya.uz.task1.config.MfaProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM for secrets at rest. The key comes only from configuration; ciphertexts carry their random nonce and
 * a version prefix so the key can be rotated later.
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    @org.springframework.beans.factory.annotation.Autowired
    public SecretCipher(MfaProperties properties) {
        this(properties.isEnabled() ? properties.getEncryptionKey() : null, properties.isEnabled());
    }

    SecretCipher(String base64Key, boolean required) {
        if (base64Key == null || base64Key.isBlank()) {
            if (required) {
                throw new IllegalStateException("MFA_ENCRYPTION_KEY is required while app.mfa.enabled=true: "
                        + "set a base64-encoded 32-byte key (openssl rand -base64 32) or disable MFA explicitly");
            }
            this.key = null;
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must be base64", exception);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("MFA_ENCRYPTION_KEY must decode to exactly 32 bytes, found " + bytes.length);
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        requireKey();
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(encrypted, 0, out, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt secret", exception);
        }
    }

    public String decrypt(String ciphertext) {
        requireKey();
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported ciphertext format");
        }
        byte[] in = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(in, 0, NONCE_BYTES)));
            return new String(cipher.doFinal(Arrays.copyOfRange(in, NONCE_BYTES, in.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt secret; was MFA_ENCRYPTION_KEY changed?", exception);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("MFA encryption is not configured");
        }
    }
}
