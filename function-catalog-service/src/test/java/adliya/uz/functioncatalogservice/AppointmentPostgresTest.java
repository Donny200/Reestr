package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.service.AppointmentRetentionJob;
import adliya.uz.functioncatalogservice.service.HelpAccessTokens;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Organization-managed appointment calendars: availability, booking without an account, no double booking, isolation. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=appointment-test-secret-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false", "catalog.scheduling.enabled=false",
        "catalog.appointments.per-client-limit=1000", "catalog.appointments.lookup-limit=1000", "catalog.appointments.max-upcoming-per-contact=3"
})
@AutoConfigureMockMvc
class AppointmentPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final AtomicInteger CLIENTS = new AtomicInteger(1);
    private static final UsernamePasswordAuthenticationToken MANAGER_10 = staff(10, List.of(10L), "APPOINTMENTS_VIEW", "APPOINTMENTS_MANAGE");
    private static final UsernamePasswordAuthenticationToken VIEWER_10 = staff(11, List.of(10L), "APPOINTMENTS_VIEW");
    private static final UsernamePasswordAuthenticationToken MANAGER_20 = staff(20, List.of(20L), "APPOINTMENTS_VIEW", "APPOINTMENTS_MANAGE");
    private static final UsernamePasswordAuthenticationToken ADMIN = superAdmin("APPOINTMENTS_VIEW", "APPOINTMENTS_MANAGE");

    @Autowired OrgFunctionService functions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppointmentRetentionJob retention;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;

    private OrgFunction service10;
    private OrgFunction service20;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE appointments, appointment_closures, appointment_schedules, audit_log_functions, audit_logs, "
                + "function_public_changes, translation_jobs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(organizations.findPublic(anyLong())).thenAnswer(call -> Optional.of(new IdentityOrganizationClient.PublicOrganization(
                call.getArgument(0), "Justice office " + call.getArgument(0), null,
                new IdentityOrganizationClient.Contact("1 Main street, Tashkent", "+998 71 000 00 00", "Mon-Fri 9-18", null))));
        service10 = published("Passport registration", 10L);
        service20 = published("Tax certificate", 20L);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void serviceWithoutCalendarIsNotBookableAndUnpublishedServicesAreHidden() throws Exception {
        mvc.perform(get("/api/appointments/services/" + service10.getId() + "/availability").with(anonymous()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookable").value(false)).andExpect(jsonPath("$.days.length()").value(0));
        login(superAdmin(with(EDITOR)));
        var draft = functions.create(new CreateOrgFunctionRequest("Draft", "D", 10L, "Docs", null));
        SecurityContextHolder.clearContext();
        enableCalendar(10L, 1);
        mvc.perform(get("/api/appointments/services/" + draft.getId() + "/availability").with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(book(draft.getId(), tomorrowSlot(service10.getId(), 0), "Ann", "+998901112233", token(), id())).andExpect(status().isNotFound());
    }

    @Test void calendarsAreManagedOnlyByTheOrganizationsOwnStaff() throws Exception {
        mvc.perform(put("/api/appointments/schedules/10").with(authentication(MANAGER_20)).contentType("application/json").content(schedule(true, 1)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/schedules/10").with(authentication(MANAGER_20))).andExpect(status().isForbidden());
        mvc.perform(put("/api/appointments/schedules/10").with(authentication(VIEWER_10)).contentType("application/json").content(schedule(true, 1)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/schedules/10").with(anonymous())).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/appointments/schedules/10").with(authentication(VIEWER_10)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.timeZone").value("Asia/Tashkent"));
        enableCalendar(10L, 1);
        mvc.perform(get("/api/appointments/schedules/10").with(authentication(ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.weeklyHours.length()").value(7));
        // Turning booking on without any opening hours is refused.
        mvc.perform(put("/api/appointments/schedules/20").with(authentication(MANAGER_20)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("enabled", true, "timeZone", "Asia/Tashkent", "slotMinutes", 30, "capacity", 1,
                        "bookingHorizonDays", 30, "minNoticeMinutes", 0, "weeklyHours", List.of())))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/appointments/schedules/20").with(authentication(MANAGER_20)).contentType("application/json")
                .content(schedule(true, 1).replace("Asia/Tashkent", "Mars/Olympus"))).andExpect(status().isBadRequest());
    }

    @Test void visitorBooksWithoutAccountAndManagesTheBookingWithItsToken() throws Exception {
        enableCalendar(10L, 1);
        JsonNode availability = availability(service10.getId(), LocalDate.now(TASHKENT).plusDays(1));
        assertThat(availability.get("bookable").asBoolean()).isTrue();
        assertThat(availability.get("timeZone").asText()).isEqualTo("Asia/Tashkent");
        String slot = availability.get("days").get(0).get("slots").get(0).get("startsAt").asText();
        String token = token();

        JsonNode booked = body(mvc.perform(book(service10.getId(), slot, "Ann Lee", "+998 (90) 111-22-33", token, id()))
                .andExpect(status().isCreated()));
        String reference = booked.get("reference").asText();
        assertThat(reference).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(booked.get("status").asText()).isEqualTo("BOOKED");
        assertThat(booked.get("organizationName").asText()).isEqualTo("Justice office 10");
        assertThat(booked.get("organizationContact").get("address").asText()).isEqualTo("1 Main street, Tashkent");
        assertThat(booked.get("serviceName").asText()).isEqualTo("Passport registration");
        assertThat(booked.get("localStart").asText()).isEqualTo("09:00");
        assertThat(booked.get("visitorPhone").asText()).isEqualTo("+998901112233");
        assertThat(booked.get("canCancel").asBoolean()).isTrue();
        assertThat(booked.toString()).doesNotContain(token);
        assertThat(jdbc.queryForObject("SELECT access_token_hash FROM appointments", String.class)).isEqualTo(HelpAccessTokens.hash(token));

        mvc.perform(get("/api/appointments/" + reference).with(anonymous()).header("X-Appointment-Token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reference").value(reference));
        mvc.perform(get("/api/appointments/" + reference).with(anonymous()).header("X-Appointment-Token", token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/appointments/" + reference).with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(get("/api/appointments/" + reference.toLowerCase()).with(anonymous()).header("X-Appointment-Token", token))
                .andExpect(status().isOk());

        // The slot is gone for everyone else, and booking it again is refused by the server.
        assertThat(availability(service10.getId(), LocalDate.now(TASHKENT).plusDays(1)).get("days").get(0).get("slots").get(0).get("startsAt").asText())
                .isNotEqualTo(slot);
        mvc.perform(book(service10.getId(), slot, "Bob", "+998902223344", token(), id())).andExpect(status().isConflict());

        // Cancelling frees the slot; cancelling twice is harmless; a cancelled booking cannot be moved.
        mvc.perform(post("/api/appointments/" + reference + "/cancel").with(anonymous()).header("X-Appointment-Token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelledBy").value("VISITOR"))
                .andExpect(jsonPath("$.canCancel").value(false));
        mvc.perform(post("/api/appointments/" + reference + "/cancel").with(anonymous()).header("X-Appointment-Token", token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/appointments/" + reference + "/reschedule").with(anonymous()).header("X-Appointment-Token", token)
                .contentType("application/json").content("{\"startsAt\":\"" + slot + "\"}")).andExpect(status().isConflict());
        mvc.perform(book(service10.getId(), slot, "Bob", "+998902223344", token(), id())).andExpect(status().isCreated());
    }

    @Test void retryingTheSameRequestReturnsTheSameBookingButAnotherTokenCannotReuseIt() throws Exception {
        enableCalendar(10L, 1);
        String slot = tomorrowSlot(service10.getId(), 0);
        String token = token();
        String request = id();
        String first = body(mvc.perform(book(service10.getId(), slot, "Ann", "+998901112233", token, request)).andExpect(status().isCreated()))
                .get("reference").asText();
        String again = body(mvc.perform(book(service10.getId(), slot, "Ann", "+998901112233", token, request)).andExpect(status().isCreated()))
                .get("reference").asText();
        assertThat(again).isEqualTo(first);
        mvc.perform(book(service10.getId(), slot, "Ann", "+998901112233", token(), request)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Long.class)).isEqualTo(1);
    }

    @Test void concurrentBookingsNeverExceedTheSlotsCapacity() throws Exception {
        enableCalendar(10L, 2);
        String slot = tomorrowSlot(service10.getId(), 0);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            int visitor = index;
            Callable<Integer> attempt = () -> {
                start.await();
                return mvc.perform(book(service10.getId(), slot, "Visitor " + visitor, "+99890000000" + visitor, token(), id()))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(2);
        assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 409);
        assertThat(jdbc.queryForList("SELECT seat FROM appointments WHERE status = 'BOOKED' ORDER BY seat", Integer.class)).containsExactly(1, 2);
    }

    @Test void visitorsMoveTheirBookingToAnotherFreeTimeOnly() throws Exception {
        enableCalendar(10L, 1);
        String first = tomorrowSlot(service10.getId(), 0);
        String second = tomorrowSlot(service10.getId(), 1);
        String third = tomorrowSlot(service10.getId(), 2);
        String token = token();
        String reference = body(mvc.perform(book(service10.getId(), first, "Ann", "+998901112233", token, id()))).get("reference").asText();
        mvc.perform(book(service10.getId(), third, "Bob", "+998902223344", token(), id())).andExpect(status().isCreated());

        mvc.perform(get("/api/appointments/" + reference + "/availability").with(anonymous()).header("X-Appointment-Token", token)
                .param("from", LocalDate.now(TASHKENT).plusDays(1).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.bookable").value(true));
        mvc.perform(post("/api/appointments/" + reference + "/reschedule").with(anonymous()).header("X-Appointment-Token", token)
                .contentType("application/json").content("{\"startsAt\":\"" + third + "\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/appointments/" + reference + "/reschedule").with(anonymous()).header("X-Appointment-Token", token)
                .contentType("application/json").content("{\"startsAt\":\"" + second + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reference").value(reference)).andExpect(jsonPath("$.startsAt").value(second))
                .andExpect(jsonPath("$.localStart").value("09:30"));
        // The original time is free again.
        mvc.perform(book(service10.getId(), first, "Cleo", "+998903334455", token(), id())).andExpect(status().isCreated());
    }

    @Test void staffSeeAndHandleOnlyTheirOrganizationsBookings() throws Exception {
        enableCalendar(10L, 1);
        enableCalendar(20L, 1);
        String slot = tomorrowSlot(service10.getId(), 0);
        String token = token();
        String reference = body(mvc.perform(book(service10.getId(), slot, "Ann Lee", "+998901112233", token, id()))).get("reference").asText();
        mvc.perform(book(service20.getId(), tomorrowSlot(service20.getId(), 0), "Tax visitor", "+998905556677", token(), id()))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/appointments/staff").with(authentication(MANAGER_10)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reference").value(reference)).andExpect(jsonPath("$[0].visitorPhone").value("+998901112233"));
        mvc.perform(get("/api/appointments/staff").with(authentication(VIEWER_10)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].visitorName").value("Ann Lee"))
                .andExpect(jsonPath("$[0].visitorPhone").doesNotExist());
        mvc.perform(get("/api/appointments/staff").param("organizationId", "10").with(authentication(MANAGER_20))).andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/staff").with(authentication(MANAGER_20)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].visitorName").value("Tax visitor"));
        mvc.perform(get("/api/appointments/staff").with(authentication(ADMIN))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/appointments/staff").with(anonymous())).andExpect(status().is4xxClientError());

        Long id = jdbc.queryForObject("SELECT id FROM appointments WHERE reference = ?", Long.class, reference);
        mvc.perform(put("/api/appointments/staff/" + id + "/status").with(authentication(MANAGER_20)).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/appointments/staff/" + id + "/status").with(authentication(VIEWER_10)).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/appointments/staff/" + id + "/status").with(authentication(MANAGER_10)).contentType("application/json")
                .content("{\"status\":\"ATTENDED\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/appointments/staff/" + id + "/status").with(authentication(MANAGER_10)).contentType("application/json")
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/appointments/" + reference).with(anonymous()).header("X-Appointment-Token", token))
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelledBy").value("STAFF"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type = 'APPOINTMENT' AND details LIKE '%Ann%'", Long.class)).isZero();
    }

    @Test void closuresRemoveDaysOrPartsOfDays() throws Exception {
        enableCalendar(10L, 1);
        LocalDate tomorrow = LocalDate.now(TASHKENT).plusDays(1);
        mvc.perform(post("/api/appointments/schedules/10/closures").with(authentication(MANAGER_10)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("kind", "HOLIDAY", "startDate", tomorrow.toString(), "endDate", tomorrow.toString(), "note", "Holiday"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.closures.length()").value(1));
        JsonNode day = availability(service10.getId(), tomorrow).get("days").get(0);
        assertThat(day.get("open").asBoolean()).isFalse();
        assertThat(day.get("slots")).isEmpty();
        Long closure = jdbc.queryForObject("SELECT id FROM appointment_closures", Long.class);
        mvc.perform(delete("/api/appointments/schedules/20/closures/" + closure).with(authentication(MANAGER_20))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/appointments/schedules/10/closures/" + closure).with(authentication(MANAGER_20))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/appointments/schedules/10/closures/" + closure).with(authentication(MANAGER_10))).andExpect(status().isOk());

        mvc.perform(post("/api/appointments/schedules/10/closures").with(authentication(MANAGER_10)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("kind", "UNAVAILABLE", "startDate", tomorrow.toString(), "endDate", tomorrow.toString(),
                        "startTime", "09:00", "endTime", "10:00")))).andExpect(status().isOk());
        assertThat(availability(service10.getId(), tomorrow).get("days").get(0).get("slots").get(0).get("start").asText()).isEqualTo("10:00");
        mvc.perform(post("/api/appointments/schedules/10/closures").with(authentication(MANAGER_10)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("kind", "CLOSED", "startDate", tomorrow.toString(), "endDate", tomorrow.minusDays(1).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test void bookingsNeedAContactAndAreLimitedPerContact() throws Exception {
        enableCalendar(10L, 1);
        mvc.perform(book(service10.getId(), tomorrowSlot(service10.getId(), 0), "Ann", null, token(), id())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
        Map<String, Object> honeypot = request(service10.getId(), tomorrowSlot(service10.getId(), 0), "Ann", "+998901112233", token(), id());
        honeypot.put("website", "http://spam.example");
        mvc.perform(post("/api/appointments").with(anonymous()).header("X-Real-IP", ip()).contentType("application/json")
                .content(json.writeValueAsString(honeypot))).andExpect(status().isBadRequest());
        for (int index = 0; index < 3; index++) {
            mvc.perform(book(service10.getId(), tomorrowSlot(service10.getId(), 0), "Ann", "+998 90 111 22 33", token(), id())).andExpect(status().isCreated());
        }
        mvc.perform(book(service10.getId(), tomorrowSlot(service10.getId(), 0), "Ann", "+998901112233", token(), id())).andExpect(status().isConflict());
        mvc.perform(book(service10.getId(), tomorrowSlot(service10.getId(), 0), "Ann", "+998 90 1112233", token(), id())).andExpect(status().isConflict());
    }

    @Test void contactDetailsAreRemovedAfterTheRetentionPeriod() throws Exception {
        enableCalendar(10L, 1);
        mvc.perform(book(service10.getId(), tomorrowSlot(service10.getId(), 0), "Ann", "+998901112233", token(), id())).andExpect(status().isCreated());
        jdbc.update("UPDATE appointments SET starts_at = now() - interval '40 days', ends_at = now() - interval '40 days' + interval '30 minutes'");
        retention.purge();
        assertThat(jdbc.queryForMap("SELECT visitor_name, visitor_phone, visitor_email, contact_removed_at FROM appointments"))
                .containsEntry("visitor_name", null).containsEntry("visitor_phone", null).containsEntry("visitor_email", null)
                .extractingByKey("contact_removed_at").isNotNull();
    }

    // ---------------------------------------------------------------- helpers

    /** Every day 09:00-18:00 with a 13:00-14:00 break, 30-minute slots, no minimum notice. */
    private String schedule(boolean enabled, int capacity) throws Exception {
        List<Map<String, Object>> hours = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            hours.add(Map.of("day", day.name(), "opens", "09:00", "closes", "18:00", "breakStarts", "13:00", "breakEnds", "14:00"));
        }
        return json.writeValueAsString(Map.of("enabled", enabled, "timeZone", "Asia/Tashkent", "slotMinutes", 30, "capacity", capacity,
                "bookingHorizonDays", 30, "minNoticeMinutes", 0, "visitorNote", "Arrive 10 minutes early", "weeklyHours", hours));
    }

    private void enableCalendar(long organization, int capacity) throws Exception {
        UsernamePasswordAuthenticationToken manager = organization == 10L ? MANAGER_10 : MANAGER_20;
        mvc.perform(put("/api/appointments/schedules/" + organization).with(authentication(manager)).contentType("application/json")
                .content(schedule(true, capacity))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    private JsonNode availability(Long service, LocalDate from) throws Exception {
        return body(mvc.perform(get("/api/appointments/services/" + service + "/availability").with(anonymous())
                .param("from", from.toString()).param("days", "3")).andExpect(status().isOk()));
    }

    /** The n-th free slot of tomorrow (09:00, 09:30, …). */
    private String tomorrowSlot(Long service, int index) throws Exception {
        return availability(service, LocalDate.now(TASHKENT).plusDays(1)).get("days").get(0).get("slots").get(index).get("startsAt").asText();
    }

    private Map<String, Object> request(Long service, String startsAt, String name, String phone, String token, String clientRequestId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("serviceId", service);
        request.put("startsAt", startsAt);
        request.put("name", name);
        if (phone != null) request.put("phone", phone);
        request.put("accessToken", token);
        request.put("clientRequestId", clientRequestId);
        request.put("language", "en");
        return request;
    }

    private org.springframework.test.web.servlet.RequestBuilder book(Long service, String startsAt, String name, String phone, String token,
                                                                     String clientRequestId) throws Exception {
        return post("/api/appointments").with(anonymous()).header("X-Real-IP", ip()).contentType("application/json")
                .content(json.writeValueAsString(request(service, startsAt, name, phone, token, clientRequestId)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String token() {
        return HelpAccessTokens.generate();
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static String ip() {
        return "192.0.2." + (CLIENTS.incrementAndGet() % 250);
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
