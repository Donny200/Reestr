package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.SubmitReportRequest;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.*;
import adliya.uz.functioncatalogservice.repository.InformationReportRepository;
import adliya.uz.functioncatalogservice.service.*;
import org.flywaydb.core.Flyway;
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

import java.util.List;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=report-workflow-test-secret-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false",
        "catalog.scheduling.enabled=false", "catalog.reports.per-client-limit=1000"
})
@AutoConfigureMockMvc
class ReportWorkflowPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final UsernamePasswordAuthenticationToken MANAGER_10 = staff(10, List.of(10L), "ORG_REPORTS_MANAGE");
    private static final UsernamePasswordAuthenticationToken VIEWER_10 = staff(11, List.of(10L), "REPORTS_VIEW");
    private static final UsernamePasswordAuthenticationToken MANAGER_20 = staff(20, List.of(20L), "ORG_REPORTS_MANAGE");

    @Autowired OrgFunctionService functions;
    @Autowired ReportSubmissionService submissions;
    @Autowired InformationReportRepository reports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE information_reports, audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(organizations.requirePublic(10L)).thenReturn(new IdentityOrganizationClient.PublicOrganization(10L, "Justice office"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void transitionsRequireRejectionExplanationAndKeepAnAuditedHistory() throws Exception {
        long reportId = report(published("Passport", 10L), ReportCategory.FEES_OR_TIMING, "Fee is outdated here", "+998 71 200-00-00");

        mvc.perform(put("/api/reports/{id}/status", reportId).with(authentication(MANAGER_10))
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.contact").value("+998 71 200-00-00"));
        mvc.perform(put("/api/reports/{id}/status", reportId).with(authentication(MANAGER_10))
                        .contentType("application/json").content("{\"status\":\"REJECTED\",\"note\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").value("An explanation is required to reject a report"));
        mvc.perform(put("/api/reports/{id}/status", reportId).with(authentication(MANAGER_10))
                        .contentType("application/json").content("{\"status\":\"NEW\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("allowed")));
        mvc.perform(put("/api/reports/{id}/status", reportId).with(authentication(MANAGER_10))
                        .contentType("application/json").content("{\"status\":\"REJECTED\",\"note\":\"The fee matches the official decree\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.contact").doesNotExist()).andExpect(jsonPath("$.contactAvailable").value(false));
        mvc.perform(put("/api/reports/{id}/status", reportId).with(authentication(MANAGER_10))
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/reports/{id}/history", reportId).with(authentication(VIEWER_10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].action").value("REPORT_RECEIVED"))
                .andExpect(jsonPath("$[0].performedBy").value("public:anonymous"))
                .andExpect(jsonPath("$[1].details").value("Report #" + reportId + ": NEW -> IN_PROGRESS"))
                .andExpect(jsonPath("$[1].performedByUserId").value(10))
                .andExpect(jsonPath("$[2].details").value("Report #" + reportId + ": IN_PROGRESS -> REJECTED\nThe fee matches the official decree"))
                .andExpect(jsonPath("$[2].performedBy").value("staff10@example.test"));
    }

    @Test void readOnlyViewersNeverSeeReporterContactAndCannotChangeStatus() throws Exception {
        long reportId = report(published("Passport", 10L), ReportCategory.OTHER, "Something is wrong", "visitor@example.uz");
        mvc.perform(get("/api/reports").with(authentication(VIEWER_10))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactAvailable").value(true))
                .andExpect(jsonPath("$[0].contact").doesNotExist());
        mvc.perform(get("/api/reports/{id}", reportId).with(authentication(VIEWER_10))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").doesNotExist());
        mvc.perform(put("/api/reports/{id}/status", reportId).with(authentication(VIEWER_10))
                .contentType("application/json").content("{\"status\":\"RESOLVED\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/{id}", reportId).with(authentication(MANAGER_10))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value("visitor@example.uz"));
    }

    @Test void staffCannotReadOrChangeAnotherOrganizationsReportsByChangingIds() throws Exception {
        long own = report(published("Own", 10L), ReportCategory.OTHER, "Own problem text", null);
        long foreign = report(published("Foreign", 20L), ReportCategory.OTHER, "Foreign problem text", null);

        mvc.perform(get("/api/reports/{id}", foreign).with(authentication(MANAGER_10))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/{id}/history", foreign).with(authentication(MANAGER_10))).andExpect(status().isForbidden());
        mvc.perform(put("/api/reports/{id}/status", foreign).with(authentication(MANAGER_10))
                .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports").param("organizationId", "20").with(authentication(MANAGER_10))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports").with(authentication(MANAGER_10))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(own));
        assertThat(reports.findById(foreign).orElseThrow().getStatus()).isEqualTo(ReportStatus.NEW);

        var admin = superAdmin("REPORTS_VIEW", "ORG_REPORTS_MANAGE");
        mvc.perform(get("/api/reports").with(authentication(admin))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/reports").param("organizationId", "20").with(authentication(admin)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(foreign));
        mvc.perform(put("/api/reports/{id}/status", foreign).with(authentication(admin))
                .contentType("application/json").content("{\"status\":\"RESOLVED\",\"note\":\"Corrected\"}")).andExpect(status().isOk());
    }

    @Test void listFiltersByStatusCategoryServiceAndDateRange() throws Exception {
        OrgFunction first = published("First", 10L);
        OrgFunction second = published("Second", 10L);
        long old = report(first, ReportCategory.OUTDATED_INFORMATION, "Outdated a while ago", null);
        long recent = report(first, ReportCategory.FEES_OR_TIMING, "Fee changed recently", null);
        long other = report(second, ReportCategory.FEES_OR_TIMING, "Fee changed for second", null);
        jdbc.update("UPDATE information_reports SET created_at = TIMESTAMPTZ '2026-01-10 10:00:00+05' WHERE id = ?", old);
        jdbc.update("UPDATE information_reports SET created_at = TIMESTAMPTZ '2026-02-28 23:30:00+05' WHERE id = ?", recent);
        jdbc.update("UPDATE information_reports SET created_at = TIMESTAMPTZ '2026-03-01 00:10:00+05' WHERE id = ?", other);

        mvc.perform(get("/api/reports").param("category", "FEES_OR_TIMING").with(authentication(VIEWER_10)))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/reports").param("entityType", "FUNCTION").param("entityId", String.valueOf(first.getId()))
                .with(authentication(VIEWER_10))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/reports").param("from", "2026-02-01").param("to", "2026-02-28").with(authentication(VIEWER_10)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(recent));
        mvc.perform(get("/api/reports").param("from", "2026-03-01").with(authentication(VIEWER_10)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(other));
        mvc.perform(get("/api/reports").param("from", "2026-03-02").param("to", "2026-03-01").with(authentication(VIEWER_10)))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/reports/{id}/status", other).with(authentication(MANAGER_10))
                .contentType("application/json").content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/reports").with(authentication(VIEWER_10))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/reports").param("status", "RESOLVED").with(authentication(VIEWER_10)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(other));
        mvc.perform(get("/api/reports").param("status", "ALL").with(authentication(VIEWER_10)))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test void reportsFollowTheCardToItsNewOrganizationAndShowLaterCorrections() throws Exception {
        OrgFunction card = published("Moving", 10L);
        long reportId = report(card, ReportCategory.OTHER, "Wrong organization", null);
        mvc.perform(get("/api/reports/{id}", reportId).with(authentication(VIEWER_10)))
                .andExpect(jsonPath("$.serviceChangedSinceReport").value(false));

        login(superAdmin(with(EDITOR)));
        functions.deactivate(card.getId());
        functions.update(card.getId(), new UpdateOrgFunctionRequest(null, "Corrected description", 20L, null, null, null, null));
        SecurityContextHolder.clearContext();

        mvc.perform(get("/api/reports/{id}", reportId).with(authentication(VIEWER_10))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/{id}", reportId).with(authentication(MANAGER_20))).andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(20))
                .andExpect(jsonPath("$.serviceChangedSinceReport").value(true))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test void migrationMapsLegacyReportStatusesWithoutLosingRows() {
        jdbc.execute("CREATE SCHEMA report_legacy");
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("report_legacy").defaultSchema("report_legacy").target("3").load().migrate();
        jdbc.execute("INSERT INTO report_legacy.information_reports (entity_type, entity_id, entity_label, organization_id, category, description, status, created_at, updated_at) VALUES "
                + "('FUNCTION', 1, 'A', 10, 'OTHER', 'In review row', 'IN_REVIEW', now(), now()),"
                + "('FUNCTION', 1, 'A', 10, 'OTHER', 'Dismissed row', 'DISMISSED', now(), now()),"
                + "('FUNCTION', 1, 'A', 10, 'OTHER', 'Resolved row', 'RESOLVED', now(), now())");
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("report_legacy").defaultSchema("report_legacy").load().migrate();
        assertThat(jdbc.queryForList("SELECT status FROM report_legacy.information_reports ORDER BY id", String.class))
                .containsExactly("IN_PROGRESS", "REJECTED", "RESOLVED");
    }

    private OrgFunction published(String name, Long organization) {
        login(superAdmin(with(EDITOR)));
        var card = functions.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null));
        functions.submitForReview(card.getId());
        OrgFunction result = functions.publish(card.getId());
        SecurityContextHolder.clearContext();
        return result;
    }

    private long report(OrgFunction card, ReportCategory category, String description, String contact) {
        submissions.submit(new SubmitReportRequest(ReportEntityType.FUNCTION, card.getId(), category, description, contact, "ru", null),
                "client-" + description.hashCode());
        return reports.findAll().stream().filter(item -> item.getDescription().equals(description)).findFirst().orElseThrow().getId();
    }
}
