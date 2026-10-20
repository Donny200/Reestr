package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.HelpProperties;
import adliya.uz.functioncatalogservice.entity.HelpGuest;
import adliya.uz.functioncatalogservice.entity.HelpGuestSession;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import adliya.uz.functioncatalogservice.repository.HelpGuestRepository;
import adliya.uz.functioncatalogservice.repository.HelpGuestSessionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Who is talking to support.
 *
 * <ul>
 *   <li>A signed-in user is identified by the account id in their verified JWT; their thread follows the account.</li>
 *   <li>An anonymous visitor gets an opaque 256-bit secret in an HttpOnly, SameSite=Strict cookie scoped to
 *       {@code /api/help} when they send their first message (never on a page view). JavaScript cannot read it, it is
 *       never put in a URL or response body, and only its SHA-256 hash is stored. IP addresses and fingerprints are
 *       not used for identity.</li>
 *   <li>An anonymous visitor may create a recovery code (shown once, stored as a hash) to attach another browser.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SupportIdentityService {

    /** Crockford-style alphabet without look-alike characters; 20 characters = 100 bits. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
    private static final int CODE_LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HelpGuestRepository guests;
    private final HelpGuestSessionRepository sessions;
    private final HelpProperties properties;
    private final CatalogAccess access;
    private final HelpRateLimiter rateLimiter;
    private final SupportRateLimiters limits;
    private final SupportEventBroker events;

    /** The caller's support identity, if any. Nothing is created and no cookie is set. */
    public record Identity(HelpGuest guest, Long sessionId) {
        public boolean account() {
            return guest.getUserId() != null;
        }
    }

    @Transactional
    public Optional<Identity> current(HttpServletRequest request) {
        Long userId = signedInUser();
        if (userId != null) {
            return guests.findByUserId(userId).map(guest -> touch(new Identity(guest, null)));
        }
        return browser(request);
    }

    /**
     * The caller's identity, created on first use. A signed-in user without a thread takes over the anonymous thread of
     * this browser (if any); its browser sessions end, so after signing out the thread is no longer reachable from
     * this browser without the account.
     */
    @Transactional
    public Identity currentOrCreate(HttpServletRequest request, HttpServletResponse response, String clientAddress) {
        Long userId = signedInUser();
        Instant now = Instant.now();
        if (userId != null) {
            Optional<HelpGuest> own = guests.findByUserId(userId);
            if (own.isPresent()) {
                return touch(new Identity(own.get(), null));
            }
            Optional<Identity> anonymous = browser(request);
            if (anonymous.isPresent() && anonymous.get().guest().getUserId() == null) {
                HelpGuest claimed = anonymous.get().guest();
                claimed.setUserId(userId);
                claimed.setRecoveryCodeHash(null);
                claimed.setLastSeenAt(now);
                sessions.deleteAllOfGuest(claimed.getId());
                events.closeBrowsers(claimed.getId());
                clearCookie(response);
                return new Identity(guests.saveAndFlush(claimed), null);
            }
            rateLimiter.acquire(clientAddress);
            return new Identity(guests.saveAndFlush(HelpGuest.builder().userId(userId).createdAt(now).lastSeenAt(now).build()), null);
        }
        Optional<Identity> existing = browser(request);
        if (existing.isPresent()) {
            return existing.get();
        }
        rateLimiter.acquire(clientAddress);
        HelpGuest guest = guests.saveAndFlush(HelpGuest.builder().createdAt(now).lastSeenAt(now).build());
        return new Identity(guest, startBrowser(guest, response).getId());
    }

    /** New recovery code for an anonymous thread; replaces the previous one. Signed-in users recover by signing in. */
    @Transactional
    public String issueRecoveryCode(Identity identity) {
        if (identity.account()) {
            throw new InvalidFieldException("code", "Your support thread is linked to your account; sign in to open it elsewhere");
        }
        String code = newCode();
        HelpGuest guest = guests.findById(identity.guest().getId()).orElseThrow();
        guest.setRecoveryCodeHash(HelpAccessTokens.hash(canonical(code)));
        guests.saveAndFlush(guest);
        return code;
    }

    /**
     * Attaches this browser to the anonymous thread that owns {@code code}. Attempts are rate limited per address; a
     * wrong code and an account-bound thread give the same answer.
     */
    @Transactional
    public Identity recover(String code, HttpServletRequest request, HttpServletResponse response, String clientAddress) {
        limits.recovery(clientAddress);
        String canonical = canonical(code);
        HelpGuest guest = canonical.length() != CODE_LENGTH ? null
                : guests.findByRecoveryCodeHash(HelpAccessTokens.hash(canonical)).filter(found -> found.getUserId() == null).orElse(null);
        if (guest == null) {
            throw new InvalidFieldException("code", "This recovery code is not valid");
        }
        if (sessions.countByGuestId(guest.getId()) >= properties.getSession().getMaxBrowsersPerGuest()) {
            throw new RateLimitExceededException(Duration.ofDays(1));
        }
        secret(request).flatMap(secret -> sessions.findBySecretHash(HelpAccessTokens.hash(secret))).ifPresent(sessions::delete);
        guest.setLastSeenAt(Instant.now());
        guests.save(guest);
        return new Identity(guest, startBrowser(guest, response).getId());
    }

    /** Removes this browser's access ("forget this browser"); the thread itself stays for staff and other browsers. */
    @Transactional
    public void forgetBrowser(HttpServletRequest request, HttpServletResponse response) {
        secret(request).flatMap(secret -> sessions.findBySecretHash(HelpAccessTokens.hash(secret))).ifPresent(session -> {
            sessions.delete(session);
            events.closeSession(session.getId());
        });
        clearCookie(response);
    }

    /** Ends idle browser sessions and drops contact details of guests inactive for the same period. */
    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now().minus(properties.getSession().getIdleTimeout());
        int removed = sessions.deleteIdleSince(cutoff);
        guests.clearIdleContacts(cutoff);
        return removed;
    }

    private Optional<Identity> browser(HttpServletRequest request) {
        Optional<String> secret = secret(request);
        if (secret.isEmpty() || !HelpAccessTokens.wellFormed(secret.get())) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return sessions.findBySecretHash(HelpAccessTokens.hash(secret.get()))
                .filter(session -> session.getLastSeenAt().plus(properties.getSession().getIdleTimeout()).isAfter(now))
                .flatMap(session -> guests.findById(session.getGuestId())
                        .filter(guest -> guest.getUserId() == null)
                        .map(guest -> {
                            if (session.getLastSeenAt().plus(properties.getSession().getTouchInterval()).isBefore(now)) {
                                sessions.touch(session.getId(), now);
                            }
                            return touch(new Identity(guest, session.getId()));
                        }));
    }

    private Identity touch(Identity identity) {
        Instant now = Instant.now();
        if (identity.guest().getLastSeenAt().plus(properties.getSession().getTouchInterval()).isBefore(now)) {
            guests.touch(identity.guest().getId(), now);
        }
        return identity;
    }

    private HelpGuestSession startBrowser(HelpGuest guest, HttpServletResponse response) {
        String secret = HelpAccessTokens.generate();
        Instant now = Instant.now();
        HelpGuestSession session = sessions.saveAndFlush(HelpGuestSession.builder()
                .guestId(guest.getId()).secretHash(HelpAccessTokens.hash(secret)).createdAt(now).lastSeenAt(now).build());
        writeCookie(response, secret, properties.getSession().getIdleTimeout());
        return session;
    }

    private Long signedInUser() {
        return access.authenticated() ? access.principal().userId() : null;
    }

    private Optional<String> secret(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (properties.getSession().getCookieName().equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void clearCookie(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        HelpProperties.Session config = properties.getSession();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(config.getCookieName(), value)
                .httpOnly(true)
                .secure(config.isCookieSecure())
                .sameSite("Strict")
                .path("/api/help")
                .maxAge(maxAge)
                .build().toString());
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder();
        for (int index = 0; index < CODE_LENGTH; index++) {
            if (index > 0 && index % 5 == 0) {
                code.append('-');
            }
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** Upper-case, without separators and spaces, so "abcd-efgh …" and "ABCDEFGH…" are the same code. */
    static String canonical(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
