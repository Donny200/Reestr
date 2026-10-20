package adliya.uz.task1.config.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpAndSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void totpCodesVerifyWithinTheWindowAndRejectOutsideIt() {
        String secret = Totp.generateSecret();
        assertThat(secret).hasSizeGreaterThanOrEqualTo(32).matches("[A-Z2-7]+");
        long now = 1_760_000_000L;
        String code = Totp.code(secret, Totp.step(now));
        assertThat(code).hasSize(6);
        assertThat(Totp.matchingStep(secret, code, now, 1)).hasValue(Totp.step(now));
        assertThat(Totp.matchingStep(secret, code, now + 30, 1)).hasValue(Totp.step(now));
        assertThat(Totp.matchingStep(secret, code, now + 90, 1)).isEmpty();
        assertThat(Totp.matchingStep(secret, "12 34 56".replace(" ", ""), now, 1)).isEmpty();
        assertThat(Totp.matchingStep(secret, "abcdef", now, 1)).isEmpty();
        assertThat(Totp.matchingStep(secret, null, now, 1)).isEmpty();
        assertThat(Totp.otpauthUri("Reestr Gov", "a@b.uz", secret))
                .startsWith("otpauth://totp/Reestr%20Gov:a%40b.uz?secret=" + secret + "&issuer=Reestr%20Gov");
    }

    @Test
    void base32RoundTrips() {
        byte[] bytes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertThat(Totp.decodeBase32(Totp.base32(bytes))).isEqualTo(bytes);
    }

    @Test
    void cipherEncryptsWithARandomNonceAndDecryptsOnlyWithTheSameKey() {
        SecretCipher cipher = new SecretCipher(KEY, true);
        String first = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String second = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
        SecretCipher other = new SecretCipher(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()), true);
        assertThatThrownBy(() -> other.decrypt(first)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cipherRequiresAValid32ByteKeyWhenMfaIsEnabled() {
        assertThatThrownBy(() -> new SecretCipher("", true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("MFA_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new SecretCipher("dG9vc2hvcnQ=", true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new SecretCipher("not base64!", true)).isInstanceOf(IllegalStateException.class);
        assertThat(new SecretCipher("", false).available()).isFalse();
    }
}
