package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.FunctionCategoryRequest;
import adliya.uz.functioncatalogservice.entity.*;
import adliya.uz.functioncatalogservice.repository.EngagementDailyCountRepository;
import adliya.uz.functioncatalogservice.repository.InformationReportRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.QualityReminderRepository;
import adliya.uz.functioncatalogservice.service.*;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=dashboard-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false",
        "catalog.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class OrganizationDashboardPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final UsernamePasswordAuthenticationToken ANALYST_10 = staff(10, List.of(10L), "ORG_ANALYTICS_VIEW");
    private static final UsernamePasswordAuthenticationToken ANALYST_20 = staff(20, List.of(20L), "ORG_ANALYTICS_VIEW");
    private static final UsernamePasswordAuthenticationToken ADMIN = superAdmin("ORG_ANALYTICS_VIEW");

    @Autowired OrgFunctionService functions;
    @Autowired OrgFunctionRepository cards;
    @Autowired FunctionCategoryService categories;
    @Autowired InformationReportRepository reports;
    @Autowired EngagementDailyCountRepository engagementRows;
    @Autowired QualityReminderRepository reminderRows;
    @Autowired QualityReminderService reminders;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean ActiveLanguageClient languages;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE quality_reminders, engagement_daily_counts, information_reports, audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(languages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "ru")));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void statusCountsAreACurrentSnapshotFilteredByCategoryAndScopedToOwnOrganizations() throws Exception {
        FunctionCategory legal = category("Legal");
        published("Passport", 10L, legal.getId(), null);
        published("Tax", 10L, null, null);
        submitted("Pending", 10L);
        draft("Draft", 10L, null);
        OrgFunction retired = published("Retired", 10L, legal.getId(), null);
        deactivate(retired);
        published("Foreign", 20L, legal.getId(), null);

        for (String[] period : List.of(new String[] {null, null}, new String[] {"2020-01-01", "2020-01-31"})) {
            var request = get("/api/analytics/summary").with(authentication(ANALYST_10));
            if (period[0] != null) {
                request = request.param("from", period[0]).param("to", period[1]);
            }
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.services.PUBLISHED").value(2))
                    .andExpect(jsonPath("$.services.PENDING_REVIEW").value(1))
                    .andExpect(jsonPath("$.services.DRAFT").value(1))
                    .andExpect(jsonPath("$.services.DEACTIVATED").value(1));
        }
        mvc.perform(get("/api/analytics/summary").param("categoryId", String.valueOf(legal.getId())).with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.services.PUBLISHED").value(1))
                .andExpect(jsonPath("$.services.PENDING_REVIEW").value(0))
                .andExpect(jsonPath("$.services.DEACTIVATED").value(1));
        mvc.perform(get("/api/analytics/summary").param("organizationId", "20").with(authentication(ANALYST_10)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/summary").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.services.PUBLISHED").value(3));
        mvc.perform(get("/api/analytics/summary").param("organizationId", "20").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.services.PUBLISHED").value(1))
                .andExpect(jsonPath("$.services.DRAFT").value(0));
        mvc.perform(get("/api/analytics/summary").with(authentication(staff(30, List.of(), "ORG_ANALYTICS_VIEW"))))
                .andExpect(jsonPath("$.services.PUBLISHED").value(0));
    }

    @Test void qualityQueueExplainsWhatToFixAndAttentionCountsMatchIt() throws Exception {
        OrgFunction healthy = published("Healthy", 10L, null, "https://gov.uz/healthy");
        translate(healthy, "ru");
        verifiedDaysAgo(healthy, 10);
        OrgFunction overdue = published("Overdue", 10L, null, "https://gov.uz/overdue");
        translate(overdue, "ru");
        verifiedDaysAgo(overdue, 200);
        OrgFunction bare = published("Bare", 10L, null, null);
        OrgFunction legacyLink = draft("Legacy link", 10L, null);
        jdbc.update("UPDATE org_functions SET official_source_url = 'www.gov.uz/page', description = NULL WHERE id = ?", legacyLink.getId());
        translate(legacyLink, "ru");
        OrgFunction retired = published("Retired", 10L, null, null);
        deactivate(retired);
        published("Foreign", 20L, null, null);

        mvc.perform(get("/api/analytics/quality-queue").with(authentication(ANALYST_10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translationsChecked").value(true))
                .andExpect(jsonPath("$.activeLanguages[0]").value("en"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Bare"))
                .andExpect(jsonPath("$.items[0].issues[*].type").value(containsInAnyOrder(
                        "VERIFICATION_OVERDUE", "TRANSLATIONS_MISSING")))
                .andExpect(jsonPath("$.items[0].issues[0].reason").value("NEVER_VERIFIED"))
                .andExpect(jsonPath("$.items[0].issues[1].details[0]").value("ru"))
                .andExpect(jsonPath("$.items[1].name").value("Overdue"))
                .andExpect(jsonPath("$.items[1].verificationStatus").value("DUE"))
                .andExpect(jsonPath("$.items[1].verificationDueAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].issues[0].reason").value("RECHECK_DUE"))
                .andExpect(jsonPath("$.items[2].name").value("Legacy link"))
                .andExpect(jsonPath("$.items[2].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[2].issues[0].reason").value("UNUSABLE"))
                .andExpect(jsonPath("$.items[2].issues[1].type").value("INFORMATION_INCOMPLETE"))
                .andExpect(jsonPath("$.items[2].issues[1].details[0]").value("description"));

        mvc.perform(get("/api/analytics/quality-queue").param("issue", "SOURCE_MISSING").with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Legacy link"));
        mvc.perform(get("/api/analytics/quality-queue").param("status", "DRAFT").with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/analytics/quality-queue").param("issue", "NOT_A_TYPE").with(authentication(ANALYST_10)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/analytics/summary").with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.servicesNeedingAttention").value(3))
                .andExpect(jsonPath("$.attention.VERIFICATION_OVERDUE").value(2))
                .andExpect(jsonPath("$.attention.SOURCE_MISSING").value(1))
                .andExpect(jsonPath("$.attention.INFORMATION_INCOMPLETE").value(1))
                .andExpect(jsonPath("$.attention.TRANSLATIONS_MISSING").value(1))
                .andExpect(jsonPath("$.translationsChecked").value(true));

        mvc.perform(get("/api/analytics/quality-queue").param("organizationId", "20").with(authentication(ANALYST_10)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/quality-queue").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.items.length()").value(4));
        mvc.perform(get("/api/analytics/quality-queue").param("organizationId", "20").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Foreign"));
    }

    @Test void translationGapsAreNotReportedWhenTheLanguageListIsUnavailable() throws Exception {
        published("Untranslated", 10L, null, "https://gov.uz/x");
        when(languages.activeLanguageCodes()).thenReturn(Optional.empty());
        mvc.perform(get("/api/analytics/summary").with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.translationsChecked").value(false))
                .andExpect(jsonPath("$.attention.TRANSLATIONS_MISSING").value(0))
                .andExpect(jsonPath("$.attention.VERIFICATION_OVERDUE").value(1));
    }

    @Test void reportCountsCoverUnresolvedReportsAndThePeriodComparison() throws Exception {
        FunctionCategory legal = category("Legal");
        OrgFunction passport = published("Passport", 10L, legal.getId(), null);
        OrgFunction tax = published("Tax", 10L, null, null);
        report(passport, ReportStatus.NEW, "2026-03-05T10:00:00+05:00");
        report(passport, ReportStatus.IN_PROGRESS, "2026-03-10T23:59:00+05:00");
        report(tax, ReportStatus.RESOLVED, "2026-02-25T09:00:00+05:00");
        report(tax, ReportStatus.NEW, "2026-02-18T23:00:00+05:00");
        report(published("Foreign", 20L, legal.getId(), null), ReportStatus.NEW, "2026-03-05T10:00:00+05:00");

        mvc.perform(get("/api/analytics/summary").param("from", "2026-03-01").param("to", "2026-03-10").with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.period.previousFrom").value("2026-02-19"))
                .andExpect(jsonPath("$.reports.unresolved").value(3))
                .andExpect(jsonPath("$.reports.newReports").value(2))
                .andExpect(jsonPath("$.reports.inProgress").value(1))
                .andExpect(jsonPath("$.reports.receivedInPeriod").value(2))
                .andExpect(jsonPath("$.reports.receivedInPreviousPeriod").value(1));
        mvc.perform(get("/api/analytics/summary").param("from", "2026-03-01").param("to", "2026-03-10")
                        .param("categoryId", String.valueOf(legal.getId())).with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.reports.unresolved").value(2))
                .andExpect(jsonPath("$.reports.receivedInPreviousPeriod").value(0));
        mvc.perform(get("/api/reports").param("status", "ALL").param("serviceCategoryId", String.valueOf(legal.getId()))
                        .with(authentication(staff(11, List.of(10L), "REPORTS_VIEW"))))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/analytics/summary").param("from", "2026-03-01").param("to", "2026-03-10").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.reports.unresolved").value(4))
                .andExpect(jsonPath("$.reports.receivedInPeriod").value(3));
    }

    @Test void remindersAreDeduplicatedThrottledAndResolvedWhenTheIssueIsFixed() throws Exception {
        OrgFunction card = published("No source", 10L, null, null);
        jdbc.update("UPDATE org_functions SET official_source_url = 'www.gov.uz/page' WHERE id = ?", card.getId());
        translate(card, "ru");
        verifiedDaysAgo(card, 1);
        published("Foreign", 20L, null, null);
        draft("Unassigned", null, null);
        Instant start = Instant.now();

        assertThat(reminders.scan(start).created()).isEqualTo(3);
        assertThat(reminders.scan(start.plus(Duration.ofHours(1))).created()).isZero();
        assertThat(reminderRows.findAll()).hasSize(3).noneMatch(row -> row.getOrganizationId() == null);

        mvc.perform(get("/api/analytics/reminders").with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].functionName").value("No source"))
                .andExpect(jsonPath("$.items[0].issue").value("SOURCE_MISSING"));
        long reminderId = reminderRows.findAll().stream().filter(row -> row.getFunctionId().equals(card.getId()))
                .findFirst().orElseThrow().getId();
        mvc.perform(get("/api/analytics/summary").with(authentication(ANALYST_10))).andExpect(jsonPath("$.openReminders").value(1));

        mvc.perform(post("/api/analytics/reminders/{id}/acknowledge", reminderId).with(authentication(ANALYST_20)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/analytics/reminders/{id}/acknowledge", reminderId).with(authentication(ANALYST_10)))
                .andExpect(status().isNoContent());
        assertThat(reminderRows.findById(reminderId).orElseThrow().getAcknowledgedByUserId()).isEqualTo(10L);
        mvc.perform(get("/api/analytics/reminders").with(authentication(ANALYST_10))).andExpect(jsonPath("$.total").value(0));

        Instant acknowledged = reminderRows.findById(reminderId).orElseThrow().getAcknowledgedAt();
        assertThat(reminders.scan(acknowledged.plus(Duration.ofDays(13))).renotified()).isZero();
        mvc.perform(get("/api/analytics/reminders").with(authentication(ANALYST_10))).andExpect(jsonPath("$.total").value(0));
        assertThat(reminders.scan(acknowledged.plus(Duration.ofDays(14))).renotified()).isEqualTo(1);
        mvc.perform(get("/api/analytics/reminders").with(authentication(ANALYST_10))).andExpect(jsonPath("$.items.length()").value(1));

        jdbc.update("UPDATE org_functions SET official_source_url = 'https://gov.uz/fixed' WHERE id = ?", card.getId());
        assertThat(reminders.scan(acknowledged.plus(Duration.ofDays(15))).resolved()).isEqualTo(1);
        mvc.perform(get("/api/analytics/reminders").with(authentication(ANALYST_10))).andExpect(jsonPath("$.total").value(0));
        assertThat(reminderRows.findById(reminderId).orElseThrow().getResolvedAt()).isNotNull();

        jdbc.update("UPDATE org_functions SET official_source_url = NULL WHERE id = ?", card.getId());
        assertThat(reminders.scan(acknowledged.plus(Duration.ofDays(16))).created()).isZero();
        jdbc.update("UPDATE org_functions SET official_source_url = 'javascript:alert(1)' WHERE id = ?", card.getId());
        assertThat(reminders.scan(acknowledged.plus(Duration.ofDays(17))).created()).isEqualTo(1);
        assertThat(reminderRows.findAll()).filteredOn(row -> row.getFunctionId().equals(card.getId())).hasSize(2);

        mvc.perform(get("/api/analytics/reminders").with(authentication(ADMIN))).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/analytics/reminders").param("categoryId", "999").with(authentication(ADMIN)))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/analytics/reminders").param("organizationId", "20").with(authentication(ANALYST_10)))
                .andExpect(status().isForbidden());
    }

    @Test void translationRemindersSurviveATemporaryLanguageOutage() {
        published("Untranslated", 10L, null, "https://gov.uz/x");
        Instant start = Instant.now();
        reminders.scan(start);
        assertThat(reminderRows.findAll()).extracting(QualityReminder::getIssue)
                .containsExactlyInAnyOrder(QualityIssueType.VERIFICATION_OVERDUE, QualityIssueType.TRANSLATIONS_MISSING);
        when(languages.activeLanguageCodes()).thenReturn(Optional.empty());
        assertThat(reminders.scan(start.plusSeconds(60)).resolved()).isZero();
        assertThat(reminderRows.findAll()).allMatch(row -> row.getResolvedAt() == null);
    }

    @Test void dashboardEndpointsRequireTheAnalyticsPermission() throws Exception {
        var editor = staff(40, List.of(10L), "FUNCTIONS_EDIT", "REPORTS_VIEW");
        for (String path : List.of("/api/analytics/summary", "/api/analytics/quality-queue", "/api/analytics/reminders")) {
            mvc.perform(get(path).with(authentication(editor))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/analytics/reminders/1/acknowledge").with(authentication(editor))).andExpect(status().isForbidden());
        mvc.perform(post("/api/analytics/reminders/999/acknowledge").with(authentication(ANALYST_10))).andExpect(status().isNotFound());
    }

    @Test void exportsApplyTheSameScopeAsTheDashboardAndNeutralizeFormulas() throws Exception {
        OrgFunction own = published("Own card", 10L, null, null);
        OrgFunction foreign = published("Foreign card", 20L, null, null);
        LocalDate day = LocalDate.of(2026, 3, 2);
        engagement(day, EngagementEventType.SERVICE_VIEW, 10L, own.getId(), 4);
        engagement(day, EngagementEventType.MAP_CLICK, 10L, own.getId(), 1);
        engagement(day, EngagementEventType.SERVICE_VIEW, 20L, foreign.getId(), 9);
        engagement(day.plusDays(1), EngagementEventType.CATALOG_VIEW, null, null, 50);

        String services = csv(get("/api/analytics/engagement/export").param("from", "2026-03-01").param("to", "2026-03-03")
                .with(authentication(ANALYST_10)));
        assertThat(services.split("\r\n")).containsExactly(
                "\uFEFFperiod_from,period_to,subject,service_id,service_name,organization_id,category,catalog_view,service_view,official_link_click,phone_click,map_click,print",
                "2026-03-01,2026-03-03,SERVICE," + own.getId() + ",Own card,10,,0,4,0,0,1,0");
        String daily = csv(get("/api/analytics/engagement/export").param("from", "2026-03-01").param("to", "2026-03-03")
                .param("breakdown", "DAILY").param("delimiter", "SEMICOLON").with(authentication(ANALYST_10)));
        assertThat(daily.split("\r\n")).containsExactly("\uFEFFdate;catalog_view;service_view;official_link_click;phone_click;map_click;print",
                "2026-03-01;0;0;0;0;0;0", "2026-03-02;0;4;0;0;1;0", "2026-03-03;0;0;0;0;0;0");
        String everything = csv(get("/api/analytics/engagement/export").param("from", "2026-03-01").param("to", "2026-03-03")
                .with(authentication(ADMIN)));
        assertThat(everything).contains("Own card", "Foreign card", "CATALOG");
        mvc.perform(get("/api/analytics/engagement/export").param("organizationId", "20").with(authentication(ANALYST_10)))
                .andExpect(status().isForbidden());

        String queue = csv(get("/api/analytics/quality-queue/export").with(authentication(ANALYST_10)));
        assertThat(queue).contains("Own card", "/admin/functions/" + own.getId(), "VERIFICATION_OVERDUE,NEVER_VERIFIED")
                .doesNotContain("Foreign card", "SOURCE_MISSING");
        assertThat(queue.split("\r\n")).hasSize(3);
        mvc.perform(get("/api/analytics/quality-queue/export").param("organizationId", "20").with(authentication(ANALYST_10)))
                .andExpect(status().isForbidden());

        report(own, ReportStatus.NEW, "2026-03-02T12:00:00+05:00", "=HYPERLINK(\"http://evil.example\",\"open\")", "visitor@example.uz");
        report(foreign, ReportStatus.NEW, "2026-03-02T12:00:00+05:00", "Foreign organization report", "other@example.uz");
        var viewer = staff(11, List.of(10L), "REPORTS_VIEW", "ORG_REPORTS_MANAGE");
        String visitorReports = csv(get("/api/reports/export").param("status", "ALL").with(authentication(viewer)));
        assertThat(visitorReports).contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"open\"\")\"", "2026-03-02 12:00:00")
                .doesNotContain("visitor@example.uz", "Foreign organization report", "contact");
        mvc.perform(get("/api/reports/export").param("organizationId", "20").with(authentication(viewer)))
                .andExpect(status().isForbidden());
        assertThat(csv(get("/api/reports/export").param("status", "ALL").with(authentication(superAdmin("REPORTS_VIEW")))))
                .contains("Foreign organization report");
    }

    private String csv(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void engagement(LocalDate day, EngagementEventType type, Long organizationId, Long functionId, long count) {
        engagementRows.save(EngagementDailyCount.builder().activityDate(day).eventType(type).organizationId(organizationId)
                .functionId(functionId).eventCount(count).build());
    }

    private void report(OrgFunction card, ReportStatus status, String createdAt) {
        report(card, status, createdAt, "Report about " + card.getName(), null);
    }

    private void report(OrgFunction card, ReportStatus status, String createdAt, String description, String contact) {
        Instant created = OffsetDateTime.parse(createdAt).toInstant();
        reports.save(InformationReport.builder().entityType(ReportEntityType.FUNCTION).entityId(card.getId())
                .entityLabel(card.getName()).organizationId(card.getOrganizationId()).category(ReportCategory.OTHER)
                .description(description).contact(contact).status(status).createdAt(created).updatedAt(created).build());
    }

    private void translate(OrgFunction card, String language) {
        OrgFunction stored = cards.findById(card.getId()).orElseThrow();
        Map<String, TranslatedText> names = new HashMap<>(stored.getNameTranslations());
        names.put(language, new TranslatedText(stored.getName() + " (" + language + ")", TranslatedText.HUMAN));
        Map<String, TranslatedText> descriptions = new HashMap<>(stored.getDescriptionTranslations());
        descriptions.put(language, new TranslatedText("Description (" + language + ")", TranslatedText.HUMAN));
        stored.setNameTranslations(names);
        stored.setDescriptionTranslations(descriptions);
        cards.save(stored);
    }

    private void verifiedDaysAgo(OrgFunction card, int days) {
        jdbc.update("UPDATE org_functions SET last_verified_at = ?, verification_outdated = false WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(days))), card.getId());
    }

    private FunctionCategory category(String name) {
        login(superAdmin(with(EDITOR)));
        FunctionCategory category = categories.create(new FunctionCategoryRequest(name, null));
        SecurityContextHolder.clearContext();
        return category;
    }

    private OrgFunction draft(String name, Long organization, String sourceUrl) {
        login(superAdmin(with(EDITOR)));
        OrgFunction card = functions.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null, null,
                "en", null, sourceUrl));
        SecurityContextHolder.clearContext();
        return card;
    }

    private OrgFunction submitted(String name, Long organization) {
        OrgFunction card = draft(name, organization, null);
        login(superAdmin(with(EDITOR)));
        OrgFunction result = functions.submitForReview(card.getId());
        SecurityContextHolder.clearContext();
        return result;
    }

    private OrgFunction published(String name, Long organization, Long categoryId, String sourceUrl) {
        login(superAdmin(with(EDITOR)));
        var card = functions.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null, categoryId,
                "en", null, sourceUrl));
        functions.submitForReview(card.getId());
        OrgFunction result = functions.publish(card.getId());
        SecurityContextHolder.clearContext();
        return result;
    }

    private void deactivate(OrgFunction card) {
        login(superAdmin(with(EDITOR)));
        functions.deactivate(card.getId());
        SecurityContextHolder.clearContext();
    }
}
