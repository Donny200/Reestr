package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.StaffMember;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.service.HelpNotificationMailer;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The visitor support thread (anonymous and signed-in), routing per organization, unread state and the staff inbox. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=help-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false", "catalog.scheduling.enabled=false",
        "catalog.help.per-client-limit=1000", "catalog.help.per-guest-limit=8", "catalog.help.recovery-attempt-limit=2",
        "catalog.help.max-unanswered-visitor-messages=6", "catalog.reports.per-client-limit=1000"
})
@AutoConfigureMockMvc
class HelpConversationPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String COOKIE = "reestr_support";
    private static final AtomicInteger CLIENTS = new AtomicInteger(1);
    private static final String[] INBOX = {"VISITOR_QUESTIONS_VIEW", "VISITOR_QUESTIONS_REPLY"};
    private static final UsernamePasswordAuthenticationToken STAFF_10 = staff(10, List.of(10L), INBOX);
    private static final UsernamePasswordAuthenticationToken STAFF_20 = staff(20, List.of(20L), INBOX);
    private static final UsernamePasswordAuthenticationToken VIEWER_10 = staff(11, List.of(10L), "VISITOR_QUESTIONS_VIEW");
    private static final UsernamePasswordAuthenticationToken ADMIN = superAdmin(INBOX);

    @Autowired OrgFunctionService functions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean HelpNotificationMailer mailer;

    private OrgFunction service10;
    private OrgFunction service20;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE help_messages, help_conversations, help_guest_sessions, help_guests, information_reports, "
                + "audit_log_functions, audit_logs, function_public_changes, translation_jobs, org_functions, function_categories "
                + "RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(organizations.requirePublic(20L)).thenReturn(new IdentityOrganizationClient.PublicOrganization(20L, "Tax office"));
        when(mailer.available()).thenReturn(false);
        service10 = published("Passport registration", 10L);
        service20 = published("Tax certificate", 20L);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void anonymousVisitorGetsOnePrivateThreadThatPersistsInTheBrowserCookie() throws Exception {
        mvc.perform(get("/api/help/support").with(anonymous()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identity").value("NONE")).andExpect(jsonPath("$.messages.length()").value(0));
        assertThat(count("help_guests")).isZero();

        MockHttpServletResponse first = visitor(post("/api/help/support/messages"), null)
                .content(body("Hello, how do I renew a passport?", null)).andExpect(status().isCreated()).andReturn().getResponse();
        Cookie browser = first.getCookie(COOKIE);
        assertThat(browser).isNotNull();
        assertThat(browser.isHttpOnly()).isTrue();
        assertThat(browser.getSecure()).isTrue();
        assertThat(browser.getPath()).isEqualTo("/api/help");
        assertThat(first.getHeader("Set-Cookie")).contains("SameSite=Strict");
        assertThat(browser.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(first.getContentAsString()).doesNotContain(browser.getValue());
        // Only the hash of the browser secret is stored.
        assertThat(jdbc.queryForObject("SELECT secret_hash FROM help_guest_sessions", String.class)).hasSize(64).isNotEqualTo(browser.getValue());

        visitor(post("/api/help/support/messages"), browser).content(body("And how long does it take?", null)).andExpect(status().isCreated());
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(browser))
                .andExpect(jsonPath("$.identity").value("BROWSER"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].body").value("Hello, how do I renew a passport?"))
                .andExpect(jsonPath("$.messages[1].body").value("And how long does it take?"))
                .andExpect(jsonPath("$.messages[0].author").value("VISITOR"));
        assertThat(count("help_conversations")).isEqualTo(1);
        assertThat(count("help_guests")).isEqualTo(1);
    }

    @Test void knowingAConversationIdOrAForgedCookieGivesNoAccess() throws Exception {
        Cookie browser = send(null, "My private question about documents", service10.getId(), null);
        Long id = conversationOf("My private question about documents");
        mvc.perform(get("/api/help/support").param("conversationId", id.toString()).with(anonymous()))
                .andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(new Cookie(COOKIE, "A".repeat(43))))
                .andExpect(jsonPath("$.identity").value("NONE"));
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(new Cookie(COOKIE, "x")))
                .andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get("/api/help/inbox/" + id).with(anonymous())).andExpect(status().isForbidden());
        mvc.perform(get("/api/help/inbox").with(anonymous())).andExpect(status().isForbidden());
        mvc.perform(post("/api/help/support/read").with(anonymous()).contentType("application/json").content("{\"upTo\":999}"))
                .andExpect(status().isNotFound());
        // Another browser has its own, separate thread.
        Cookie other = send(null, "Someone else entirely here", null, null);
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(other))
                .andExpect(jsonPath("$.messages.length()").value(1)).andExpect(jsonPath("$.messages[0].body").value("Someone else entirely here"));
        assertThat(browser.getValue()).isNotEqualTo(other.getValue());
    }

    @Test void signedInVisitorsKeepTheirThreadOnTheirAccountAndTakeOverTheBrowserThread() throws Exception {
        var user77 = token("user77@example.test", "ROLE_MODERATOR", List.of(), List.of(), 77L);
        var user78 = token("user78@example.test", "ROLE_MODERATOR", List.of(), List.of(), 78L);
        MockHttpServletResponse response = mvc.perform(post("/api/help/support/messages").with(authentication(user77))
                        .header("X-Real-IP", ip()).contentType("application/json").content(body("Question from my account", null)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        assertThat(response.getCookie(COOKIE)).isNull();
        assertThat(jdbc.queryForObject("SELECT user_id FROM help_guests", Long.class)).isEqualTo(77L);
        mvc.perform(get("/api/help/support").with(authentication(user77)))
                .andExpect(jsonPath("$.identity").value("ACCOUNT")).andExpect(jsonPath("$.messages[0].body").value("Question from my account"));
        mvc.perform(get("/api/help/support").with(authentication(user78))).andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get("/api/help/support").with(anonymous())).andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(post("/api/help/support/recovery-code").with(authentication(user77))).andExpect(status().isBadRequest());

        // An anonymous browser thread is taken over by the account on its first signed-in message.
        Cookie browser = send(null, "Asked before signing in", null, null);
        MockHttpServletResponse claimed = mvc.perform(post("/api/help/support/messages").with(authentication(user78)).cookie(browser)
                        .header("X-Real-IP", ip()).contentType("application/json").content(body("Now signed in", null)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        assertThat(claimed.getHeader("Set-Cookie")).contains("Max-Age=0");
        mvc.perform(get("/api/help/support").with(authentication(user78)))
                .andExpect(jsonPath("$.messages.length()").value(2)).andExpect(jsonPath("$.messages[0].body").value("Asked before signing in"));
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(browser)).andExpect(jsonPath("$.messages.length()").value(0));
    }

    @Test void messagesAreRoutedToTheResponsibleOrganizationButShownAsOneThread() throws Exception {
        Cookie browser = send(null, "General question about the site", null, null);
        send(browser, "About passports please", service10.getId(), null);
        send(browser, "And about the tax certificate", service20.getId(), null);
        send(browser, "Passports again, a follow-up", service10.getId(), null);
        assertThat(count("help_conversations")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT organization_id FROM help_conversations ORDER BY id", Long.class)).containsExactly(null, 10L, 20L);

        mvc.perform(get("/api/help/support").with(anonymous()).cookie(browser))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[1].serviceId").value(service10.getId()))
                .andExpect(jsonPath("$.messages[1].contextLabel").value("Passport registration"))
                .andExpect(jsonPath("$.messages[1].organizationId").value(10))
                .andExpect(jsonPath("$.messages[2].organizationId").value(20))
                .andExpect(jsonPath("$.messages[0].organizationId").doesNotExist());

        Long passport = conversationOf("About passports please");
        Long tax = conversationOf("And about the tax certificate");
        Long general = conversationOf("General question about the site");
        String inbox10 = mvc.perform(get("/api/help/inbox").with(authentication(STAFF_10)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(passport))
                .andExpect(jsonPath("$[0].unreadCount").value(2)).andExpect(jsonPath("$[0].serviceName").value("Passport registration"))
                .andReturn().getResponse().getContentAsString();
        assertThat(inbox10).doesNotContain("tax certificate", "General question");
        mvc.perform(get("/api/help/inbox/" + passport).with(authentication(STAFF_10)))
                .andExpect(jsonPath("$.messages.length()").value(2)).andExpect(jsonPath("$.messages[1].body").value("Passports again, a follow-up"));
        for (Long foreign : List.of(tax, general)) {
            mvc.perform(get("/api/help/inbox/" + foreign).with(authentication(STAFF_10))).andExpect(status().isForbidden());
            mvc.perform(post("/api/help/inbox/" + foreign + "/replies").with(authentication(STAFF_10)).contentType("application/json")
                    .content("{\"message\":\"Not mine\"}")).andExpect(status().isForbidden());
            mvc.perform(put("/api/help/inbox/" + foreign + "/status").with(authentication(STAFF_10)).contentType("application/json")
                    .content("{\"status\":\"CLOSED\"}")).andExpect(status().isForbidden());
            mvc.perform(put("/api/help/inbox/" + foreign + "/assignee").with(authentication(STAFF_10)).contentType("application/json")
                    .content("{\"userId\":10}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/help/inbox/" + foreign + "/read").with(authentication(STAFF_10)).contentType("application/json")
                    .content("{\"upTo\":999}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/help/inbox").param("organizationId", "20").with(authentication(STAFF_10))).andExpect(status().isForbidden());
        mvc.perform(get("/api/help/inbox").param("general", "true").with(authentication(STAFF_10))).andExpect(status().isForbidden());
        mvc.perform(get("/api/help/inbox").with(authentication(STAFF_20))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(tax));
        mvc.perform(get("/api/help/inbox").with(authentication(ADMIN))).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/help/inbox").param("general", "true").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].general").value(true));
        mvc.perform(get("/api/help/inbox").param("organizationId", "20").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(tax));
        mvc.perform(get("/api/help/inbox").with(authentication(staff(12, List.of(10L), "REPORTS_VIEW")))).andExpect(status().isForbidden());
    }

    @Test void staffRepliesReachTheThreadAndUnreadStateIsPersistedOnBothSides() throws Exception {
        Cookie browser = send(null, "Can I apply on a weekend?", service10.getId(), null);
        Long id = conversationOf("Can I apply on a weekend?");
        mvc.perform(get("/api/help/inbox/unread").with(authentication(STAFF_10)))
                .andExpect(jsonPath("$.conversations").value(1)).andExpect(jsonPath("$.messages").value(1));
        mvc.perform(get("/api/help/inbox").param("unread", "true").with(authentication(STAFF_10))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/help/inbox/unread").with(authentication(STAFF_20))).andExpect(jsonPath("$.conversations").value(0));

        mvc.perform(post("/api/help/inbox/" + id + "/replies").with(authentication(VIEWER_10)).contentType("application/json")
                .content("{\"message\":\"Yes\"}")).andExpect(status().isForbidden());
        String reply = "{\"message\":\"Yes, centres are open on Saturday.\",\"clientMessageId\":\"staff-reply-0001\"}";
        mvc.perform(post("/api/help/inbox/" + id + "/replies").with(authentication(STAFF_10)).contentType("application/json").content(reply))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversation.status").value("ANSWERED"))
                .andExpect(jsonPath("$.conversation.unreadCount").value(0));
        // A retried reply with the same client id is not posted twice.
        mvc.perform(post("/api/help/inbox/" + id + "/replies").with(authentication(STAFF_10)).contentType("application/json").content(reply))
                .andExpect(jsonPath("$.messages.length()").value(2));
        mvc.perform(get("/api/help/inbox/unread").with(authentication(STAFF_10))).andExpect(jsonPath("$.conversations").value(0));

        Long visitorMessage = jdbc.queryForObject("SELECT id FROM help_messages WHERE author = 'VISITOR'", Long.class);
        String view = mvc.perform(get("/api/help/support/updates").param("after", visitorMessage.toString()).with(anonymous()).cookie(browser))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.messages[?(@.author == 'STAFF')].body").value("Yes, centres are open on Saturday."))
                .andReturn().getResponse().getContentAsString();
        assertThat(view).doesNotContain("staff10@example.test", "staffUserId", "assignedUser");
        Long replyId = json.readTree(view).get("latestReplyId").asLong();
        mvc.perform(get("/api/help/support/unread").with(anonymous()).cookie(browser))
                .andExpect(jsonPath("$.unread").value(1)).andExpect(jsonPath("$.latestReplyId").value(replyId));

        visitor(post("/api/help/support/read"), browser).content("{\"upTo\":" + replyId + "}").andExpect(jsonPath("$.unread").value(0));
        // After a "reload" the state comes from the database.
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(browser))
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.messages[0].readAt").isNotEmpty())
                .andExpect(jsonPath("$.messages[1].readAt").isNotEmpty());

        send(browser, "Thanks! One more detail please", service10.getId(), null);
        assertThat(jdbc.queryForObject("SELECT status FROM help_conversations WHERE id = ?", String.class, id)).isEqualTo("IN_PROGRESS");
        mvc.perform(put("/api/help/inbox/" + id + "/status").with(authentication(STAFF_10)).contentType("application/json")
                .content("{\"status\":\"ANSWERED\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/help/inbox/" + id + "/status").with(authentication(STAFF_10)).contentType("application/json")
                .content("{\"status\":\"CLOSED\"}")).andExpect(jsonPath("$.conversation.status").value("CLOSED"));
        mvc.perform(post("/api/help/inbox/" + id + "/replies").with(authentication(STAFF_10)).contentType("application/json")
                .content("{\"message\":\"Late\"}")).andExpect(status().isConflict());
        // The thread is persistent: writing again reopens the closed conversation.
        send(browser, "Sorry, I have another question", service10.getId(), null);
        assertThat(jdbc.queryForObject("SELECT status FROM help_conversations WHERE id = ?", String.class, id)).isEqualTo("NEW");
        mvc.perform(put("/api/help/inbox/" + id + "/status").with(authentication(STAFF_10)).contentType("application/json")
                .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(jsonPath("$.conversation.status").value("IN_PROGRESS"));
        assertThat(count("help_conversations")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_type = 'HELP_CONVERSATION' ORDER BY id", String.class))
                .containsExactly("HELP_REPLY", "HELP_STATUS_CHANGE", "HELP_STATUS_CHANGE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE details LIKE '%Saturday%'", Long.class)).isZero();
    }

    @Test void reportsOfIncorrectInformationArriveAsCategorizedMessagesInTheSameThread() throws Exception {
        Cookie browser = send(null, "General hello first", null, null);
        send(browser, "The fee on this page is wrong, it costs 10 000", service10.getId(), "FEES_OR_TIMING");
        Long reportId = jdbc.queryForObject("SELECT id FROM information_reports", Long.class);
        assertThat(jdbc.queryForMap("SELECT entity_type, entity_id, organization_id, category, contact FROM information_reports"))
                .containsEntry("entity_type", "FUNCTION").containsEntry("entity_id", service10.getId())
                .containsEntry("organization_id", 10L).containsEntry("category", "FEES_OR_TIMING").containsEntry("contact", null);
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(browser))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].category").value("FEES_OR_TIMING"))
                .andExpect(jsonPath("$.messages[1].serviceId").value(service10.getId()));
        Long id = conversationOf("The fee on this page is wrong, it costs 10 000");
        mvc.perform(get("/api/help/inbox/" + id).with(authentication(STAFF_10)))
                .andExpect(jsonPath("$.messages[0].category").value("FEES_OR_TIMING"))
                .andExpect(jsonPath("$.messages[0].reportId").value(reportId));
        // Organization pages report about the organization itself.
        send(browser, "The phone number of this office is wrong", null, "CONTACT_DETAILS", 20L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_reports WHERE entity_type = 'ORGANIZATION' AND organization_id = 20", Long.class)).isEqualTo(1);

        visitor(post("/api/help/support/messages"), browser).content(json(Map.of("body", "A report without any subject at all",
                "clientMessageId", UUID.randomUUID().toString(), "reportCategory", "OTHER"))).andExpect(status().isBadRequest());
        visitor(post("/api/help/support/messages"), browser).content(json(Map.of("body", "Wrong address of the service",
                "clientMessageId", UUID.randomUUID().toString(), "serviceId", service10.getId(), "reportCategory", "CONTACT_DETAILS")))
                .andExpect(status().isBadRequest());
        visitor(post("/api/help/support/messages"), browser).content(json(Map.of("body", "short",
                "clientMessageId", UUID.randomUUID().toString(), "serviceId", service10.getId(), "reportCategory", "OTHER")))
                .andExpect(status().isBadRequest());
        assertThat(count("information_reports")).isEqualTo(2);
    }

    @Test void inputIsValidatedRetriesAreIdempotentAndVisitorsAreRateLimited() throws Exception {
        Cookie browser = send(null, "First message here", null, null);
        visitor(post("/api/help/support/messages"), browser).content(body("", null)).andExpect(status().isBadRequest());
        visitor(post("/api/help/support/messages"), browser).content(body("x".repeat(2001), null)).andExpect(status().isBadRequest());
        visitor(post("/api/help/support/messages"), browser).content(body("My card is 8600 1234 5678 9012", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.body").exists());
        visitor(post("/api/help/support/messages"), browser).content(json(Map.of("body", "Bad id", "clientMessageId", "../x")))
                .andExpect(status().isBadRequest());
        visitor(post("/api/help/support/messages"), browser).content(body("About a missing service", 999999L)).andExpect(status().isNotFound());
        visitor(post("/api/help/support/messages"), browser).header("Origin", "https://evil.example").content(body("Cross-site attempt", null))
                .andExpect(status().isForbidden());

        String clientId = "retry-" + UUID.randomUUID();
        String retried = json(Map.of("body", "Sent twice by a flaky network", "clientMessageId", clientId));
        Long firstId = json.readTree(visitor(post("/api/help/support/messages"), browser).content(retried)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        visitor(post("/api/help/support/messages"), browser).content(retried).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(firstId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM help_messages WHERE client_message_id = ?", Long.class, clientId)).isEqualTo(1);

        // Six unanswered messages in a row is the configured maximum (rejected attempts above also count toward the
        // per-identity limit, so a fresh browser is used here).
        Cookie quiet = send(null, "Still waiting 0", null, null);
        for (int index = 1; index < 6; index++) {
            send(quiet, "Still waiting " + index, null, null);
        }
        visitor(post("/api/help/support/messages"), quiet).content(body("One more without reply", null)).andExpect(status().isConflict());

        // Per-identity limit: 8 messages per window regardless of the visitor address.
        Cookie busy = send(null, "Busy visitor 0", service10.getId(), null);
        Long busyConversation = conversationOf("Busy visitor 0");
        for (int index = 1; index < 8; index++) {
            if (index % 3 == 0) {
                mvc.perform(post("/api/help/inbox/" + busyConversation + "/replies").with(authentication(STAFF_10))
                        .contentType("application/json").content("{\"message\":\"ok\"}")).andExpect(status().isOk());
            }
            send(busy, "Busy visitor " + index, service10.getId(), null);
        }
        visitor(post("/api/help/support/messages"), busy).content(body("Ninth message", service10.getId())).andExpect(status().isTooManyRequests());
    }

    @Test void contactDetailsAreOptionalVisibleOnlyToStaffWhoMayReplyAndNoticesNeedConfiguredMail() throws Exception {
        Cookie browser = send(null, "No contact details needed", service10.getId(), null);
        Long id = conversationOf("No contact details needed");
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(browser))
                .andExpect(jsonPath("$.contact.email").doesNotExist()).andExpect(jsonPath("$.notificationsAvailable").value(false));
        visitor(put("/api/help/support/contact"), browser).content("{\"email\":\"visitor@example.test\",\"notifyByEmail\":true}")
                .andExpect(status().isBadRequest());
        visitor(put("/api/help/support/contact"), browser).content("{\"email\":\"not-an-email\",\"notifyByEmail\":false}")
                .andExpect(status().isBadRequest());
        visitor(put("/api/help/support/contact"), browser).content("{\"email\":\"visitor@example.test\",\"notifyByEmail\":false}")
                .andExpect(jsonPath("$.email").value("visitor@example.test"));

        mvc.perform(get("/api/help/inbox/" + id).with(authentication(STAFF_10))).andExpect(jsonPath("$.contact.email").value("visitor@example.test"));
        String hidden = mvc.perform(get("/api/help/inbox/" + id).with(authentication(VIEWER_10)))
                .andExpect(jsonPath("$.conversation.contactAvailable").value(true)).andReturn().getResponse().getContentAsString();
        String list = mvc.perform(get("/api/help/inbox").with(authentication(STAFF_10))).andReturn().getResponse().getContentAsString();
        assertThat(hidden + list).doesNotContain("visitor@example.test");

        when(mailer.available()).thenReturn(true);
        visitor(put("/api/help/support/contact"), browser).content("{\"email\":\"visitor@example.test\",\"notifyByEmail\":true}")
                .andExpect(jsonPath("$.notifyByEmail").value(true));
        mvc.perform(post("/api/help/inbox/" + id + "/replies").with(authentication(STAFF_10)).contentType("application/json")
                .content("{\"message\":\"Here is your answer.\"}")).andExpect(status().isOk());
        verify(mailer).sendReplyNotice("visitor@example.test");
        visitor(put("/api/help/support/contact"), browser).content("{\"email\":\"\",\"notifyByEmail\":false}").andExpect(jsonPath("$.email").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM help_guests WHERE contact_email IS NOT NULL", Long.class)).isZero();
        verify(mailer, times(1)).sendReplyNotice(anyString());
    }

    @Test void aRecoveryCodeRestoresTheAnonymousThreadInAnotherBrowserAndForgettingABrowserEndsItsAccess() throws Exception {
        Cookie original = send(null, "Thread I want to keep", null, null);
        String code = json.readTree(visitor(post("/api/help/support/recovery-code"), original).content("")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("code").asText();
        assertThat(code).matches("[A-Z2-9]{5}(-[A-Z2-9]{5}){3}");
        assertThat(jdbc.queryForObject("SELECT recovery_code_hash FROM help_guests", String.class)).hasSize(64).doesNotContain(code);
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(original)).andExpect(jsonPath("$.recoveryCodeSet").value(true));

        String address = ip();
        mvc.perform(post("/api/help/support/recover").with(anonymous()).header("X-Real-IP", address).contentType("application/json")
                .content("{\"code\":\"WRONG-WRONG-WRONG-WRONG\"}")).andExpect(status().isBadRequest());
        MockHttpServletResponse restored = mvc.perform(post("/api/help/support/recover").with(anonymous()).header("X-Real-IP", address)
                        .contentType("application/json").content("{\"code\":\"" + code.toLowerCase().replace("-", " ") + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].body").value("Thread I want to keep"))
                .andReturn().getResponse();
        Cookie second = restored.getCookie(COOKIE);
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(original.getValue());
        send(second, "Written from the second device", null, null);
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(original)).andExpect(jsonPath("$.messages.length()").value(2));
        mvc.perform(post("/api/help/support/recover").with(anonymous()).header("X-Real-IP", address).contentType("application/json")
                .content("{\"code\":\"" + code + "\"}")).andExpect(status().isTooManyRequests());

        MockHttpServletResponse forgotten = mvc.perform(delete("/api/help/support/session").with(anonymous()).cookie(second))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(forgotten.getHeader("Set-Cookie")).contains("Max-Age=0");
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(second)).andExpect(jsonPath("$.identity").value("NONE"));
        mvc.perform(get("/api/help/support").with(anonymous()).cookie(original)).andExpect(jsonPath("$.messages.length()").value(2));
    }

    @Test void assignmentIsLimitedToActiveMembersOfTheResponsibleOrganization() throws Exception {
        send(null, "Who handles my question please?", service10.getId(), null);
        Long id = conversationOf("Who handles my question please?");
        when(organizations.activeMembers(10L)).thenReturn(List.of(new StaffMember(7L, "Ann", "Lee", "ann@example.test", "ROLE_MODERATOR")));
        mvc.perform(put("/api/help/inbox/" + id + "/assignee").with(authentication(STAFF_10)).contentType("application/json")
                .content("{\"userId\":99}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/help/inbox/" + id + "/assignee").with(authentication(STAFF_10)).contentType("application/json")
                .content("{\"userId\":7}")).andExpect(jsonPath("$.conversation.assignedUserName").value("Ann Lee"));
        mvc.perform(get("/api/help/inbox").param("assignee", "me").with(authentication(STAFF_10))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/help/inbox").param("sort", "UNREAD_FIRST").with(authentication(STAFF_10))).andExpect(jsonPath("$[0].id").value(id));
    }

    // ---------------------------------------------------------------- helpers

    private Cookie send(Cookie browser, String text, Long serviceId, String reportCategory) throws Exception {
        return send(browser, text, serviceId, reportCategory, null);
    }

    private Cookie send(Cookie browser, String text, Long serviceId, String reportCategory, Long organizationId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("body", text);
        request.put("clientMessageId", UUID.randomUUID().toString());
        if (serviceId != null) request.put("serviceId", serviceId);
        if (organizationId != null) request.put("organizationId", organizationId);
        if (reportCategory != null) request.put("reportCategory", reportCategory);
        request.put("language", "en");
        MockHttpServletResponse response = visitor(post("/api/help/support/messages"), browser).content(json(request))
                .andExpect(status().isCreated()).andReturn().getResponse();
        Cookie issued = response.getCookie(COOKIE);
        return issued != null ? issued : browser;
    }

    private String body(String text, Long serviceId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("body", text);
        request.put("clientMessageId", UUID.randomUUID().toString());
        if (serviceId != null) request.put("serviceId", serviceId);
        return json(request);
    }

    private String json(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private Long conversationOf(String text) {
        return jdbc.queryForObject("SELECT conversation_id FROM help_messages WHERE body = ? AND author = 'VISITOR'", Long.class, text);
    }

    private static String ip() {
        return "198.51.100." + (CLIENTS.incrementAndGet() % 250);
    }

    /** Each request comes from its own address, so only the per-identity limit applies where a test wants it. */
    private Visitor visitor(MockHttpServletRequestBuilder request, Cookie browser) {
        MockHttpServletRequestBuilder built = request.with(anonymous()).header("X-Real-IP", ip()).contentType("application/json");
        return new Visitor(browser == null ? built : built.cookie(browser));
    }

    private final class Visitor {
        private MockHttpServletRequestBuilder request;
        Visitor(MockHttpServletRequestBuilder request) { this.request = request; }
        Visitor header(String name, String value) { request = request.header(name, value); return this; }
        ResultActions content(String body) throws Exception { return mvc.perform(request.content(body)); }
    }

    private OrgFunction published(String name, Long organization) {
        login(superAdmin(with(EDITOR)));
        var card = functions.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null));
        functions.submitForReview(card.getId());
        OrgFunction result = functions.publish(card.getId());
        SecurityContextHolder.clearContext();
        return result;
    }
}
