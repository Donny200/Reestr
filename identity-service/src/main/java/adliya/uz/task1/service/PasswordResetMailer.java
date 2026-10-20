package adliya.uz.task1.service;

import adliya.uz.task1.config.PasswordResetProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Delivers self-service reset links by e-mail. Delivery is considered available only when the operator enabled it,
 * configured SMTP (spring.mail.host), a sender address and the public frontend origin. Nothing about the token is
 * logged.
 */
@Component
@Slf4j
public class PasswordResetMailer {

    private final PasswordResetProperties properties;
    private final ObjectProvider<JavaMailSender> senders;

    public PasswordResetMailer(PasswordResetProperties properties, ObjectProvider<JavaMailSender> senders) {
        this.properties = properties;
        this.senders = senders;
    }

    public boolean available() {
        return properties.getMail().isEnabled()
                && StringUtils.hasText(properties.getMail().getFrom())
                && StringUtils.hasText(properties.getPublicBaseUrl())
                && senders.getIfAvailable() != null;
    }

    /** Explains, for operators, which configuration is missing; never shown to anonymous visitors. */
    public String missingConfiguration() {
        if (!properties.getMail().isEnabled()) {
            return "app.password-reset.mail.enabled is false";
        }
        if (senders.getIfAvailable() == null) {
            return "spring.mail.host is not configured";
        }
        if (!StringUtils.hasText(properties.getMail().getFrom())) {
            return "app.password-reset.mail.from is not configured";
        }
        if (!StringUtils.hasText(properties.getPublicBaseUrl())) {
            return "app.password-reset.public-base-url is not configured";
        }
        return "";
    }

    public void sendResetLink(String email, String link, Instant expiresAt) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Mail delivery is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(email);
        message.setSubject("ReestrGov: password reset / сброс пароля / parolni tiklash");
        message.setText("""
                Open the link below to choose a new password. It can be used once and expires at %1$s (UTC).
                If you did not request a reset, ignore this message; your password stays unchanged.

                Откройте ссылку ниже, чтобы задать новый пароль. Ссылка одноразовая и действует до %1$s (UTC).
                Если вы не запрашивали сброс, проигнорируйте это письмо; пароль останется прежним.

                Yangi parol tanlash uchun quyidagi havolani oching. Havola bir marta ishlaydi va %1$s (UTC) gacha amal qiladi.
                Agar siz tiklashni so‘ramagan bo‘lsangiz, bu xatni e’tiborsiz qoldiring; parolingiz o‘zgarmaydi.

                %2$s
                """.formatted(expiresAt, link));
        sender.send(message);
    }
}
