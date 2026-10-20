package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.service.HelpNotificationMailer;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.SupportEventBroker;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live support notices over server-sent events: who receives which event, that events carry ids but never message
 * text, that event ids are unique for de-duplication, and that the REST endpoints remain the source of truth.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=stream-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false", "catalog.scheduling.enabled=false",
        "catalog.help.per-client-limit=1000", "catalog.help.per-guest-limit=100", "catalog.help.stream.max-per-guest=2",
        "catalog.reports.per-client-limit=1000"
})
@AutoConfigureMockMvc
class SupportEventStreamPostgresTest {
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
    private static final UsernamePasswordAuthenticationToken ADMIN = superAdmin(INBOX);

    @Autowired OrgFunctionService functions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SupportEventBroker broker;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean HelpNotificationMailer mailer;

    private OrgFunction service10;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE help_messages, help_conversations, help_guest_sessions, help_guests, information_reports, "
                + "audit_log_functions, audit_logs, function_public_changes, translation_jobs, org_functions, function_categories "
                + "RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(mailer.available()).thenReturn(false);
        broker.closeAll();
        service10 = published("Passport registration", 10L);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void visitorWithoutThreadGetsNoStreamAndAnonymousCannotOpenTheInboxStream() throws Exception {
        mvc.perform(get("/api/help/support/stream").with(anonymous())).andExpect(status().isNoContent());
        mvc.perform(get("/api/help/inbox/stream").with(anonymous())).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/help/inbox/stream").with(authentication(staff(30, List.of(30L), "FUNCTIONS_VIEW"))))
                .andExpect(status().isForbidden());
    }

    @Test void staffReplyReachesTheVisitorStreamAtOnceWithoutMessageText() throws Exception {
        Cookie browser = firstMessage("Secret question text about my passport");
        MvcResult visitorStream = mvc.perform(get("/api/help/support/stream").with(anonymous()).cookie(browser))
                .andExpect(request().asyncStarted()).andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
        assertThat(await(visitorStream, "ready")).isNotEmpty();

        Long conversation = jdbc.queryForObject("SELECT id FROM help_conversations", Long.class);
        mvc.perform(post("/api/help/inbox/" + conversation + "/replies").with(authentication(STAFF_10)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("message", "Private reply text", "clientMessageId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());

        Long replyId = jdbc.queryForObject("SELECT id FROM help_messages WHERE author = 'STAFF'", Long.class);
        assertThat(await(visitorStream, "support", "MESSAGE")).anySatisfy(event -> assertThat(event[2]).contains("\"type\":\"MESSAGE\"").contains("\"messageId\":" + replyId));
        String raw = visitorStream.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("Private reply text").doesNotContain("Secret question text");

        // The REST thread is the source of truth: the reply is there and unread until the visitor reads it.
        String thread = mvc.perform(get("/api/help/support").with(anonymous()).cookie(browser)).andReturn().getResponse().getContentAsString();
        assertThat(thread).contains("Private reply text").contains("\"unread\":1");

        // Reading it notifies the visitor's other tabs (READ).
        mvc.perform(post("/api/help/support/read").with(anonymous()).cookie(browser).contentType("application/json")
                .content("{\"upTo\":" + replyId + "}")).andExpect(status().isOk());
        assertThat(await(visitorStream, "support", "\"type\":\"READ\"")).hasSize(1);
        assertThat(ids(visitorStream)).doesNotHaveDuplicates();
    }

    @Test void inboxStreamsOnlyCarryConversationsOfTheStaffMembersOrganizations() throws Exception {
        MvcResult own = mvc.perform(get("/api/help/inbox/stream").with(authentication(STAFF_10))).andExpect(request().asyncStarted()).andReturn();
        MvcResult other = mvc.perform(get("/api/help/inbox/stream").with(authentication(STAFF_20))).andExpect(request().asyncStarted()).andReturn();
        MvcResult global = mvc.perform(get("/api/help/inbox/stream").with(authentication(ADMIN))).andExpect(request().asyncStarted()).andReturn();
        await(own, "ready");
        await(other, "ready");
        await(global, "ready");

        firstMessage("Question about the passport service");
        Long conversation = jdbc.queryForObject("SELECT id FROM help_conversations WHERE organization_id = 10", Long.class);
        assertThat(await(own, "inbox", "\"conversationId\":" + conversation)).isNotEmpty();
        assertThat(await(global, "inbox", "\"conversationId\":" + conversation)).isNotEmpty();

        // A general question goes to central support: only global staff hear about it.
        visitorSend(null, "A general question about this site");
        Long central = jdbc.queryForObject("SELECT id FROM help_conversations WHERE organization_id IS NULL", Long.class);
        assertThat(await(global, "inbox", "\"conversationId\":" + central)).isNotEmpty();
        Thread.sleep(300);
        assertThat(events(other)).noneMatch(event -> event[1].equals("inbox"));
        assertThat(events(own)).noneMatch(event -> event[2].contains("\"conversationId\":" + central));
        assertThat(own.getResponse().getContentAsString()).doesNotContain("Question about the passport service");
    }

    @Test void streamsPerVisitorAreLimited() throws Exception {
        Cookie browser = firstMessage("Hello");
        int before = broker.openStreams();
        assertThat(before).isZero();
        mvc.perform(get("/api/help/support/stream").with(anonymous()).cookie(browser)).andExpect(request().asyncStarted());
        mvc.perform(get("/api/help/support/stream").with(anonymous()).cookie(browser)).andExpect(request().asyncStarted());
        mvc.perform(get("/api/help/support/stream").with(anonymous()).cookie(browser))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        assertThat(broker.openStreams()).isEqualTo(before + 2);
    }

    @Test void forgettingTheBrowserEndsItsStream() throws Exception {
        Cookie browser = firstMessage("Hello");
        int before = broker.openStreams();
        mvc.perform(get("/api/help/support/stream").with(anonymous()).cookie(browser)).andExpect(request().asyncStarted());
        assertThat(broker.openStreams()).isEqualTo(before + 1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/help/support/session")
                .with(anonymous()).cookie(browser)).andExpect(status().isNoContent());
        assertThat(broker.openStreams()).isEqualTo(before);
        mvc.perform(get("/api/help/support/stream").with(anonymous()).cookie(browser)).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- helpers

    private Cookie firstMessage(String text) throws Exception {
        var response = mvc.perform(post("/api/help/support/messages").with(anonymous()).header("X-Real-IP", ip())
                .contentType("application/json").content(body(text, service10.getId()))).andExpect(status().isCreated()).andReturn().getResponse();
        return response.getCookie(COOKIE);
    }

    private void visitorSend(Cookie browser, String text) throws Exception {
        var request = post("/api/help/support/messages").with(anonymous()).header("X-Real-IP", ip()).contentType("application/json")
                .content(body(text, null));
        mvc.perform(browser == null ? request : request.cookie(browser)).andExpect(status().isCreated());
    }

    private String body(String text, Long serviceId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("body", text);
        request.put("clientMessageId", UUID.randomUUID().toString());
        if (serviceId != null) request.put("serviceId", serviceId);
        return json.writeValueAsString(request);
    }

    /** Events of this stream so far: {id, name, data}. */
    private static List<String[]> events(MvcResult stream) throws Exception {
        List<String[]> found = new ArrayList<>();
        for (String frame : stream.getResponse().getContentAsString().split("\n\n")) {
            String id = null, name = null, data = null;
            for (String line : frame.split("\n")) {
                if (line.startsWith("id:")) id = line.substring(3);
                else if (line.startsWith("event:")) name = line.substring(6);
                else if (line.startsWith("data:")) data = line.substring(5);
            }
            if (name != null && data != null) {
                found.add(new String[]{id, name, data});
            }
        }
        return found;
    }

    private static List<String> ids(MvcResult stream) throws Exception {
        return events(stream).stream().map(event -> event[0]).toList();
    }

    private static List<String[]> await(MvcResult stream, String name) throws Exception {
        return await(stream, name, "");
    }

    /** Waits (up to 5 s) for an event with this name whose data contains {@code text}; events are sent asynchronously. */
    private static List<String[]> await(MvcResult stream, String name, String text) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            List<String[]> matching = events(stream).stream().filter(event -> event[1].equals(name) && event[2].contains(text)).toList();
            if (!matching.isEmpty()) {
                return matching;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("No '" + name + "' event in: " + stream.getResponse().getContentAsString());
    }

    private static String ip() {
        return "203.0.113." + (CLIENTS.incrementAndGet() % 250);
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
