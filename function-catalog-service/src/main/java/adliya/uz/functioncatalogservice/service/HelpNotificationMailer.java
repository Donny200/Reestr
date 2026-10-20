package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.HelpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Optional e-mail notice for the support chat. Delivery counts as available only when the operator enabled it and
 * configured SMTP (spring.mail.host), a sender and the public SPA origin; otherwise the visitor only gets the in-app
 * unread indicator. Visitors opt in with their own address. The notice contains no message text and no secret;
 * addresses and message texts are never logged, and a delivery failure never fails the staff member's request.
 */
@Component
@Slf4j
public class HelpNotificationMailer {

    private final HelpProperties properties;
    private final ObjectProvider<JavaMailSender> senders;

    public HelpNotificationMailer(HelpProperties properties, ObjectProvider<JavaMailSender> senders) {
        this.properties = properties;
        this.senders = senders;
    }

    public boolean available() {
        return properties.getMail().isEnabled()
                && StringUtils.hasText(properties.getMail().getFrom())
                && StringUtils.hasText(properties.getPublicBaseUrl())
                && configured(senders.getIfAvailable());
    }

    /** An empty SPRING_MAIL_HOST still creates a sender bean; it only counts when it has a host. */
    private static boolean configured(JavaMailSender sender) {
        return sender instanceof JavaMailSenderImpl impl ? StringUtils.hasText(impl.getHost()) : sender != null;
    }

    /** Only says that a reply is waiting; the message itself is read on the site, in the visitor's own support thread. */
    public void sendReplyNotice(String email) {
        String page = properties.getPublicBaseUrl().replaceAll("/+$", "") + "/support";
        send(email, "ReestrGov: a reply from support / ответ поддержки / qo‘llab-quvvatlash javobi", """
                Support replied to your message. Open the Support page in the browser you used (or restore it with your recovery code) to read it:

                Поддержка ответила на ваше сообщение. Откройте страницу «Поддержка» в том же браузере (или восстановите доступ кодом восстановления):

                Qo‘llab-quvvatlash xabaringizga javob berdi. Javobni o‘qish uchun o‘sha brauzerda «Qo‘llab-quvvatlash» sahifasini oching (yoki tiklash kodi bilan kiring):

                %s
                """.formatted(page));
    }

    private void send(String email, String subject, String text) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || !available()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException exception) {
            log.warn("Visitor question notification could not be delivered: {}", exception.getClass().getSimpleName());
        }
    }
}
