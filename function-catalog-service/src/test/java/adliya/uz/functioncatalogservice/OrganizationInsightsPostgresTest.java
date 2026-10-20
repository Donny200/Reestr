package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.FunctionCategoryRequest;
import adliya.uz.functioncatalogservice.entity.*;
import adliya.uz.functioncatalogservice.repository.EngagementDailyCountRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=insights-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false",
        "catalog.scheduling.enabled=false", "catalog.analytics.per-client-limit=1000"
})
@AutoConfigureMockMvc
class OrganizationInsightsPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    private static final UsernamePasswordAuthenticationToken ANALYST_10 = staff(10, List.of(10L), "ORG_ANALYTICS_VIEW");

    @Autowired OrgFunctionService functions;
    @Autowired FunctionCategoryService categories;
    @Autowired EngagementInsightsService engagement;
    @Autowired EngagementDailyCountRepository counts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE engagement_daily_counts, information_reports, audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void eventOwnershipIsDerivedFromTheStoredServiceNotFromTheClient() throws Exception {
        OrgFunction own = published("Own", 10L, null);
        event("{\"type\":\"SERVICE_VIEW\",\"serviceId\":%d,\"organizationId\":20}".formatted(own.getId()));
        event("{\"type\":\"PRINT\",\"serviceId\":%d}".formatted(own.getId()));
        assertThat(counts.findAll()).hasSize(2).allSatisfy(row -> {
            assertThat(row.getOrganizationId()).isEqualTo(10L);
            assertThat(row.getFunctionId()).isEqualTo(own.getId());
            assertThat(row.getActivityDate()).isEqualTo(LocalDate.now(ZONE));
            assertThat(row.getEventCount()).isEqualTo(1);
        });
        event("{\"type\":\"SERVICE_VIEW\",\"serviceId\":%d}".formatted(own.getId()));
        assertThat(counts.findAll()).filteredOn(row -> row.getEventType() == EngagementEventType.SERVICE_VIEW)
                .singleElement().extracting(EngagementDailyCount::getEventCount).isEqualTo(2L);
    }

    @Test void onlyPublicSubjectsAndAnonymousVisitorsAreCounted() throws Exception {
        OrgFunction draft = draft("Draft", 10L);
        published("Visible", 10L, null);
        event("{\"type\":\"SERVICE_VIEW\",\"serviceId\":%d}".formatted(draft.getId()));
        event("{\"type\":\"SERVICE_VIEW\",\"serviceId\":999999}");
        event("{\"type\":\"CATALOG_VIEW\",\"organizationId\":77}");
        mvc.perform(post("/api/analytics/events").with(authentication(ANALYST_10)).contentType("application/json")
                .content("{\"type\":\"CATALOG_VIEW\",\"organizationId\":10}")).andExpect(status().isAccepted());
        assertThat(counts.count()).isZero();

        event("{\"type\":\"CATALOG_VIEW\",\"organizationId\":10}");
        event("{\"type\":\"CATALOG_VIEW\"}");
        assertThat(counts.findAll()).extracting(EngagementDailyCount::getOrganizationId).containsExactlyInAnyOrder(10L, null);
    }

    @Test void malformedSubjectsAreRejected() throws Exception {
        for (String body : List.of("{\"type\":\"SERVICE_VIEW\"}", "{\"type\":\"PRINT\",\"organizationId\":10}",
                "{\"type\":\"CATALOG_VIEW\",\"serviceId\":1}", "{\"type\":\"UNKNOWN\"}", "{\"serviceId\":1}",
                "{\"type\":\"PHONE_CLICK\",\"serviceId\":-4}")) {
            mvc.perform(post("/api/analytics/events").with(anonymous()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(counts.count()).isZero();
    }

    @Test void periodTotalsComparePreviousPeriodAndRespectCategoryAndOrganizationScope() throws Exception {
        var legal = category("Legal");
        var tax = category("Tax");
        OrgFunction passport = published("Passport", 10L, legal.getId());
        OrgFunction taxCard = published("Tax certificate", 10L, tax.getId());
        OrgFunction foreign = published("Foreign", 20L, legal.getId());
        LocalDate march1 = LocalDate.of(2026, 3, 1);
        row(march1, EngagementEventType.SERVICE_VIEW, 10L, passport.getId(), 5);
        row(march1.plusDays(9), EngagementEventType.SERVICE_VIEW, 10L, taxCard.getId(), 7);
        row(march1.plusDays(9), EngagementEventType.PHONE_CLICK, 10L, passport.getId(), 2);
        row(march1.plusDays(3), EngagementEventType.CATALOG_VIEW, 10L, null, 4);
        row(march1.plusDays(3), EngagementEventType.SERVICE_VIEW, 20L, foreign.getId(), 50);
        row(march1.plusDays(3), EngagementEventType.CATALOG_VIEW, null, null, 100);
        row(march1.minusDays(1), EngagementEventType.SERVICE_VIEW, 10L, passport.getId(), 3);
        row(march1.minusDays(10), EngagementEventType.SERVICE_VIEW, 10L, passport.getId(), 1);
        row(march1.minusDays(11), EngagementEventType.SERVICE_VIEW, 10L, passport.getId(), 1000);

        mvc.perform(get("/api/analytics/engagement").param("from", "2026-03-01").param("to", "2026-03-10")
                        .with(authentication(ANALYST_10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.previousFrom").value("2026-02-19"))
                .andExpect(jsonPath("$.period.previousTo").value("2026-02-28"))
                .andExpect(jsonPath("$.period.timeZone").value("Asia/Tashkent"))
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.totals.SERVICE_VIEW").value(12))
                .andExpect(jsonPath("$.totals.CATALOG_VIEW").value(4))
                .andExpect(jsonPath("$.totals.PHONE_CLICK").value(2))
                .andExpect(jsonPath("$.totals.PRINT").value(0))
                .andExpect(jsonPath("$.previousTotals.SERVICE_VIEW").value(4))
                .andExpect(jsonPath("$.series.length()").value(10))
                .andExpect(jsonPath("$.series[0].counts.SERVICE_VIEW").value(5))
                .andExpect(jsonPath("$.series[1].counts.SERVICE_VIEW").value(0))
                .andExpect(jsonPath("$.series[9].counts.SERVICE_VIEW").value(7))
                .andExpect(jsonPath("$.topServices[0].name").value("Tax certificate"))
                .andExpect(jsonPath("$.topServices[0].views").value(7))
                .andExpect(jsonPath("$.topServices[1].name").value("Passport"))
                .andExpect(jsonPath("$.topServices[1].actions").value(2))
                .andExpect(jsonPath("$.topServices.length()").value(2));

        mvc.perform(get("/api/analytics/engagement").param("from", "2026-03-01").param("to", "2026-03-10")
                        .param("categoryId", String.valueOf(legal.getId())).with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.totals.SERVICE_VIEW").value(5))
                .andExpect(jsonPath("$.totals.CATALOG_VIEW").value(0))
                .andExpect(jsonPath("$.totals.PHONE_CLICK").value(2))
                .andExpect(jsonPath("$.topServices.length()").value(1));

        mvc.perform(get("/api/analytics/engagement").param("organizationId", "20").with(authentication(ANALYST_10)))
                .andExpect(status().isForbidden());

        var admin = superAdmin("ORG_ANALYTICS_VIEW");
        mvc.perform(get("/api/analytics/engagement").param("from", "2026-03-01").param("to", "2026-03-10").with(authentication(admin)))
                .andExpect(jsonPath("$.totals.SERVICE_VIEW").value(62))
                .andExpect(jsonPath("$.totals.CATALOG_VIEW").value(104))
                .andExpect(jsonPath("$.topServices[0].name").value("Foreign"));
        mvc.perform(get("/api/analytics/engagement").param("from", "2026-03-01").param("to", "2026-03-10")
                        .param("organizationId", "20").with(authentication(admin)))
                .andExpect(jsonPath("$.totals.SERVICE_VIEW").value(50))
                .andExpect(jsonPath("$.totals.CATALOG_VIEW").value(0));
    }

    @Test void longPeriodsUseWeeksAlignedToMondayAndClippedToThePeriod() throws Exception {
        OrgFunction card = published("Weekly", 10L, null);
        row(LocalDate.of(2026, 1, 1), EngagementEventType.SERVICE_VIEW, 10L, card.getId(), 1);
        row(LocalDate.of(2026, 1, 4), EngagementEventType.SERVICE_VIEW, 10L, card.getId(), 2);
        row(LocalDate.of(2026, 1, 5), EngagementEventType.SERVICE_VIEW, 10L, card.getId(), 4);
        mvc.perform(get("/api/analytics/engagement").param("from", "2026-01-01").param("to", "2026-03-31")
                        .with(authentication(ANALYST_10)))
                .andExpect(jsonPath("$.granularity").value("WEEK"))
                .andExpect(jsonPath("$.series[0].start").value("2026-01-01"))
                .andExpect(jsonPath("$.series[0].end").value("2026-01-04"))
                .andExpect(jsonPath("$.series[0].counts.SERVICE_VIEW").value(3))
                .andExpect(jsonPath("$.series[1].start").value("2026-01-05"))
                .andExpect(jsonPath("$.series[1].counts.SERVICE_VIEW").value(4))
                .andExpect(jsonPath("$.series[-1:].end").value("2026-03-31"));
    }

    @Test void expiredAggregatesArePurgedAccordingToRetention() {
        OrgFunction card = published("Old", 10L, null);
        LocalDate today = LocalDate.now(ZONE);
        row(today.minusDays(731), EngagementEventType.SERVICE_VIEW, 10L, card.getId(), 3);
        row(today.minusDays(729), EngagementEventType.SERVICE_VIEW, 10L, card.getId(), 3);
        assertThat(engagement.purgeExpired()).isEqualTo(1);
        assertThat(counts.findAll()).singleElement().extracting(EngagementDailyCount::getActivityDate).isEqualTo(today.minusDays(729));
    }

    private void event(String body) throws Exception {
        mvc.perform(post("/api/analytics/events").with(anonymous()).header("X-Real-IP", "198.51.100.20")
                .contentType("application/json").content(body)).andExpect(status().isAccepted());
    }

    private void row(LocalDate day, EngagementEventType type, Long organizationId, Long functionId, long count) {
        counts.save(EngagementDailyCount.builder().activityDate(day).eventType(type).organizationId(organizationId)
                .functionId(functionId).eventCount(count).build());
    }

    private FunctionCategory category(String name) {
        login(superAdmin(with(EDITOR)));
        FunctionCategory category = categories.create(new FunctionCategoryRequest(name, null));
        SecurityContextHolder.clearContext();
        return category;
    }

    private OrgFunction draft(String name, Long organization) {
        login(superAdmin(with(EDITOR)));
        OrgFunction card = functions.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null));
        SecurityContextHolder.clearContext();
        return card;
    }

    private OrgFunction published(String name, Long organization, Long categoryId) {
        login(superAdmin(with(EDITOR)));
        var card = functions.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null, categoryId));
        functions.submitForReview(card.getId());
        OrgFunction result = functions.publish(card.getId());
        SecurityContextHolder.clearContext();
        return result;
    }
}
