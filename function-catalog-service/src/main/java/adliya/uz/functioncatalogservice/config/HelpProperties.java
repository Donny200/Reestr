package adliya.uz.functioncatalogservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Support chat: abuse limits, the anonymous guest session cookie, recovery codes and optional e-mail notices. */
@Component
@ConfigurationProperties(prefix = "catalog.help")
@Getter
@Setter
public class HelpProperties implements ClientLimitProperties {
    /** Visitor messages per visitor address and window (rate limit; addresses are never stored). */
    private int perClientLimit = 20;
    private Duration perClientWindow = Duration.ofMinutes(10);
    /** Visitor messages per support identity and window. */
    private int perGuestLimit = 20;
    private Duration perGuestWindow = Duration.ofMinutes(10);
    /** New anonymous identities and recovery attempts, all visitors together. */
    private int globalLimit = 2_000;
    private Duration globalWindow = Duration.ofHours(1);
    private int maxTrackedClients = 10_000;
    /** Visitor messages in a row, in one routed conversation, without a staff reply in between. */
    private int maxUnansweredVisitorMessages = 10;
    /** Messages kept in one routed conversation. */
    private int maxMessages = 500;
    /** Recovery-code attempts per visitor address and window. */
    private int recoveryAttemptLimit = 5;
    private Duration recoveryAttemptWindow = Duration.ofMinutes(15);
    /** Absolute public origin of the SPA (e.g. https://reestr.example); used in e-mail notices. */
    private String publicBaseUrl = "";
    /** Browser origins allowed to send cookie-authenticated support requests (besides publicBaseUrl). */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:3000", "http://localhost:5173", "http://127.0.0.1:5173", "http://127.0.0.1:5183"));
    private Session session = new Session();
    private Mail mail = new Mail();
    private Stream stream = new Stream();

    /**
     * Server-sent events that tell open support pages and staff inboxes that something changed. Events carry only
     * ids, never message text; clients then reload from the REST API, which stays the source of truth.
     */
    @Getter
    @Setter
    public static class Stream {
        private boolean enabled = true;
        /** A comment line is sent this often so proxies keep the connection open (nginx reads time out after 60 s). */
        private Duration heartbeat = Duration.ofSeconds(20);
        /** Connections end after this long; the browser reconnects and is authorized again. */
        private Duration timeout = Duration.ofMinutes(10);
        /** Open streams per support identity (tabs and devices) and in total on one service instance. */
        private int maxPerGuest = 5;
        private int maxPerUser = 5;
        private int maxTotal = 2_000;
    }

    /** Anonymous guest session cookie (HttpOnly, SameSite=Strict, path /api/help). */
    @Getter
    @Setter
    public static class Session {
        private String cookieName = "reestr_support";
        /** true for HTTPS deployments; the local HTTP Compose stack sets it from APP_COOKIE_SECURE=false. */
        private boolean cookieSecure = true;
        /** A browser session (and the guest's contact details) is dropped after this long without activity. */
        private Duration idleTimeout = Duration.ofDays(180);
        /** last_seen_at is written at most this often. */
        private Duration touchInterval = Duration.ofMinutes(10);
        /** Browsers attached to one guest through its recovery code. */
        private int maxBrowsersPerGuest = 10;
    }

    @Getter
    @Setter
    public static class Mail {
        /** Reply notices are offered only when this is true, a sender is set and SMTP (spring.mail.host) is configured. */
        private boolean enabled = false;
        private String from = "";
    }
}
