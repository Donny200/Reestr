package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.FeedbackVoteRequest;
import adliya.uz.functioncatalogservice.dto.ServiceInstructions;
import adliya.uz.functioncatalogservice.dto.StaffMember;
import adliya.uz.functioncatalogservice.dto.SubmitReportRequest;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.FunctionPublicChange;
import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.PublicChangeType;
import adliya.uz.functioncatalogservice.entity.QualityReminder;
import adliya.uz.functioncatalogservice.entity.ReportCategory;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.AuditLogRepository;
import adliya.uz.functioncatalogservice.repository.FunctionPublicChangeRepository;
import adliya.uz.functioncatalogservice.repository.InformationReportRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.QualityReminderRepository;
import adliya.uz.functioncatalogservice.service.ActiveLanguageClient;
import adliya.uz.functioncatalogservice.service.FeedbackService;
import adliya.uz.functioncatalogservice.service.FunctionAssignmentService;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.PublicChangeService;
import adliya.uz.functioncatalogservice.service.QualityReminderService;
import adliya.uz.functioncatalogservice.service.ReportReviewService;
import adliya.uz.functioncatalogservice.service.ReportSubmissionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.login;
import static adliya.uz.functioncatalogservice.support.TestPrincipals.staff;
import static adliya.uz.functioncatalogservice.support.TestPrincipals.superAdmin;
import static adliya.uz.functioncatalogservice.support.TestPrincipals.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public change history, responsible staff assignments and anonymous usefulness feedback on PostgreSQL. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=history-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false",
        "catalog.scheduling.enabled=false", "catalog.feedback.per-client-limit=3"
})
@AutoConfigureMockMvc
class PublicHistoryAssignmentFeedbackPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final List<String> EDITOR = List.of("FUNCTIONS_VIEW", "FUNCTIONS_CREATE", "FUNCTIONS_EDIT", "FUNCTIONS_SUBMIT_REVIEW",
            "FUNCTIONS_REVIEW", "FUNCTIONS_PUBLISH", "FUNCTIONS_REACTIVATE", "FUNCTIONS_DEACTIVATE", "AUDIT_VIEW");
    private static final StaffMember ANN = new StaffMember(7L, "Ann", "Lee", "ann@example.test", "ROLE_MODERATOR");
    private static final StaffMember BOB = new StaffMember(8L, "Bob", "Ray", "bob@example.test", "ROLE_ORG_ADMIN");

    @Autowired OrgFunctionService service;
    @Autowired FunctionAssignmentService assignments;
    @Autowired PublicChangeService publicChanges;
    @Autowired QualityReminderService reminders;
    @Autowired FeedbackService feedback;
    @Autowired ReportSubmissionService submissions;
    @Autowired OrgFunctionRepository cards;
    @Autowired FunctionPublicChangeRepository changes;
    @Autowired QualityReminderRepository reminderRows;
    @Autowired InformationReportRepository reportRows;
    @Autowired AuditLogRepository audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean ActiveLanguageClient languages;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE feedback_daily_counts, function_public_changes, quality_reminders, engagement_daily_counts, information_reports, "
                + "audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(organizations.activeMembers(10L)).thenReturn(List.of(ANN, BOB));
        when(organizations.activeMembers(20L)).thenReturn(List.of(new StaffMember(9L, "Zed", "Foreign", "zed@example.test", "ROLE_ORG_ADMIN")));
        when(languages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en")));
        login(superAdmin(with(EDITOR)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    // ---- public change history -------------------------------------------------------------------------------------

    @Test void onlyApprovedPublicationsReachThePublicHistoryAndNothingInternalLeaks() throws Exception {
        OrgFunction draft = service.create(new CreateOrgFunctionRequest("Passport", "Renewal", 10L, "Old passport", null));
        service.update(draft.getId(), new UpdateOrgFunctionRequest("Passport", "Renewal of a passport", null, null, null, null, null));
        perform(get("/api/functions/{id}/history", draft.getId()).with(anonymous())).andExpect(status().isNotFound());
        service.submitForReview(draft.getId());
        service.reject(draft.getId(), "Internal note: fee missing, ask Karim");
        assertThat(changes.findAll()).as("draft edits, submission and rejection write nothing public").isEmpty();
        service.submitForReview(draft.getId());
        OrgFunction card = service.publish(draft.getId());
        Long id = card.getId();

        perform(get("/api/functions/{id}/history", id).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].changeType").value("FIRST_PUBLICATION"))
                .andExpect(jsonPath("$[0].sections", hasSize(0)))
                .andExpect(jsonPath("$[0].publishedAt").exists());
        perform(get("/api/functions/{id}", id).with(anonymous())).andExpect(jsonPath("$.lastUpdatedAt").exists());

        OrgFunction revision = service.createRevision(id);
        service.update(revision.getId(), new UpdateOrgFunctionRequest(null, null, null, null, null, null, null,
                new ServiceInstructions(null, "Fill in the form\nPay the fee", null, null, null, "50 000 UZS"), null));
        service.submitForReview(revision.getId());
        service.reject(revision.getId(), "Rejected: wrong fee, see ticket 42");
        perform(get("/api/functions/{id}/history", id).with(anonymous())).andExpect(jsonPath("$", hasSize(1)));
        service.update(revision.getId(), new UpdateOrgFunctionRequest(null, null, null, null, null, null, null,
                new ServiceInstructions(null, "Fill in the form\nPay the fee", null, null, null, "60 000 UZS"), null));
        service.submitForReview(revision.getId());
        service.publish(revision.getId());

        String history = perform(get("/api/functions/{id}/history", id).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].changeType").value("UPDATE"))
                .andExpect(jsonPath("$[0].sections", containsInAnyOrder("steps", "fee")))
                .andReturn().getResponse().getContentAsString();
        for (String forbidden : List.of("Karim", "ticket 42", "performedBy", "userId", "email", "example.test", "sectionHashes", "Rejected", "50 000")) {
            assertThat(history).doesNotContain(forbidden);
        }
        assertThat(audit.findFunctionHistory(id)).extracting(log -> log.getAction()).contains(AuditAction.REJECT);
        perform(get("/api/functions/{id}/audit", id).with(anonymous())).andExpect(status().isForbidden());

        Instant lastPublished = changes.findFirstByFunctionIdOrderByPublishedAtDescIdDesc(id).orElseThrow().getPublishedAt();
        perform(get("/api/functions").with(anonymous()))
                .andExpect(jsonPath("$[0].lastUpdatedAt").value(lastPublished.toString()))
                .andExpect(jsonPath("$[0].assignee").doesNotExist());

        service.deactivate(id);
        perform(get("/api/functions/{id}/history", id).with(anonymous())).andExpect(status().isNotFound());
        service.reactivate(id);
        service.publish(id);
        List<FunctionPublicChange> all = changes.findTop20ByFunctionIdOrderByPublishedAtDescIdDesc(id);
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getChangeType()).isEqualTo(PublicChangeType.REPUBLISHED);
        assertThat(publicChanges.backfill(Instant.now())).as("existing entries are never duplicated").isZero();
    }

    @Test void backfillGivesPreviouslyPublishedCardsAFirstEntryDatedByTheInternalPublication() {
        OrgFunction card = published("Tax", 10L);
        changes.deleteAll();
        assertThat(publicChanges.backfill(Instant.now())).isEqualTo(1);
        FunctionPublicChange entry = changes.findFirstByFunctionIdOrderByPublishedAtDescIdDesc(card.getId()).orElseThrow();
        assertThat(entry.getChangeType()).isEqualTo(PublicChangeType.FIRST_PUBLICATION);
        assertThat(entry.getSectionHashes()).containsKeys("name", "documents", "fee", "translations");
        assertThat(entry.getPublishedAt()).isEqualTo(audit.findFunctionHistory(card.getId()).stream()
                .filter(log -> log.getAction() == AuditAction.PUBLISH).findFirst().orElseThrow().getPerformedAt());
    }

    // ---- responsible staff -----------------------------------------------------------------------------------------

    @Test void assignmentsAreScopedToOwnOrganizationAndOnlyActiveMembersAreAccepted() throws Exception {
        OrgFunction own = published("Own", 10L);
        OrgFunction foreign = published("Foreign", 20L);
        var orgAdmin = staff(8, List.of(10L), "FUNCTIONS_VIEW", "FUNCTIONS_ASSIGN", "ORG_ANALYTICS_VIEW");
        var noPermission = staff(8, List.of(10L), "FUNCTIONS_VIEW");

        perform(put("/api/functions/{id}/assignee", own.getId()).with(authentication(noPermission)).contentType("application/json")
                .content("{\"userId\":7}")).andExpect(status().isForbidden());
        perform(put("/api/functions/{id}/assignee", foreign.getId()).with(authentication(orgAdmin)).contentType("application/json")
                .content("{\"userId\":9}")).andExpect(status().isForbidden());
        perform(get("/api/functions/{id}/assignee/candidates", foreign.getId()).with(authentication(orgAdmin))).andExpect(status().isForbidden());
        perform(put("/api/functions/{id}/assignee", own.getId()).with(authentication(orgAdmin)).contentType("application/json")
                .content("{\"userId\":99}")).andExpect(status().isBadRequest());
        perform(put("/api/functions/{id}/assignee", own.getId()).with(authentication(orgAdmin)).contentType("application/json")
                .content("{\"userId\":9}")).andExpect(status().isBadRequest());
        perform(get("/api/functions/{id}/assignee/candidates", own.getId()).with(authentication(orgAdmin))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(7, 8)));

        perform(put("/api/functions/{id}/assignee", own.getId()).with(authentication(orgAdmin)).contentType("application/json")
                .content("{\"userId\":7}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.userId").value(7))
                .andExpect(jsonPath("$.assignee.name").value("Ann Lee"))
                .andExpect(jsonPath("$.assignee.email").value("ann@example.test"));
        perform(get("/api/functions/{id}", own.getId()).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());
        perform(get("/api/functions/{id}/admin", own.getId()).with(authentication(orgAdmin)))
                .andExpect(jsonPath("$.assignee.userId").value(7));
        assertThat(audit.findFunctionHistory(own.getId())).filteredOn(log -> log.getAction() == AuditAction.ASSIGNEE_CHANGE)
                .singleElement().satisfies(log -> assertThat(log.getDetails()).contains("nobody -> #7 (ann@example.test)"));

        login(superAdmin("FUNCTIONS_ASSIGN", "FUNCTIONS_VIEW", "FUNCTIONS_EDIT"));
        assertThat(assignments.assign(foreign.getId(), 9L).getAssignedUserId()).isEqualTo(9L);
        OrgFunction revision = service.createRevision(own.getId());
        assertThatThrownBy(() -> assignments.assign(revision.getId(), 8L)).isInstanceOf(WorkflowConflictException.class);
        assertThat(cards.findById(revision.getId()).orElseThrow().getAssignedUserId()).as("revisions carry no assignment").isNull();
        service.discardRevision(own.getId());

        login(staff(8, List.of(10L), "FUNCTIONS_ASSIGN"));
        assertThatThrownBy(() -> assignments.unassign(foreign.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(assignments.unassign(own.getId()).getAssignedUserId()).isNull();
    }

    @Test void deactivatedOrRemovedStaffAreFlaggedForReassignmentAndWorkIsRoutedToTheAssignee() throws Exception {
        OrgFunction first = published("First", 10L);
        OrgFunction second = published("Second", 10L);
        OrgFunction third = published("Third", 10L);
        published("Foreign", 20L);
        assignments.assign(first.getId(), 7L);
        assignments.assign(second.getId(), 8L);
        var ann = staff(7, List.of(10L), "FUNCTIONS_VIEW", "ORG_ANALYTICS_VIEW", "REPORTS_VIEW", "FUNCTIONS_ASSIGN");

        // Ann (7) loses access: identity no longer lists her as an active member.
        when(organizations.activeMembers(10L)).thenReturn(List.of(BOB));
        perform(get("/api/functions/assignments").with(authentication(ann))).andExpect(status().isOk())
                .andExpect(jsonPath("$.membersChecked").value(true))
                .andExpect(jsonPath("$.counts.assigned").value(1))
                .andExpect(jsonPath("$.counts.unassigned").value(1))
                .andExpect(jsonPath("$.counts.needsReassignment").value(1))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[?(@.functionId == " + first.getId() + ")].state").value("NEEDS_REASSIGNMENT"))
                .andExpect(jsonPath("$.items[?(@.functionId == " + third.getId() + ")].state").value("UNASSIGNED"));
        perform(get("/api/functions/assignments").param("state", "UNASSIGNED").with(authentication(ann)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].functionId").value(third.getId()));
        perform(get("/api/functions/assignments").param("organizationId", "20").with(authentication(ann))).andExpect(status().isForbidden());
        assertThat(cards.findById(first.getId()).orElseThrow().getAssignedUserId()).as("nobody is reassigned silently").isEqualTo(7L);
        perform(put("/api/functions/{id}/assignee", third.getId()).with(authentication(ann)).contentType("application/json")
                .content("{\"userId\":7}")).andExpect(status().isBadRequest());
        perform(get("/api/functions/assignments").with(authentication(superAdmin("FUNCTIONS_VIEW"))))
                .andExpect(jsonPath("$.items", hasSize(4)));

        // Reminders and reports follow the assignee; organization-level views keep everything.
        reminders.scan(Instant.now());
        assertThat(reminderRows.findAll()).extracting(QualityReminder::getAssignedUserId).contains(7L, 8L, null);
        perform(get("/api/analytics/reminders").param("assignee", "me").with(authentication(ann)))
                .andExpect(jsonPath("$.items[*].functionId", containsInAnyOrder(first.getId().intValue())))
                .andExpect(jsonPath("$.items[0].assignedUserId").value(7));
        perform(get("/api/analytics/reminders").with(authentication(ann)))
                .andExpect(jsonPath("$.items[*].functionId", containsInAnyOrder(first.getId().intValue(), second.getId().intValue(), third.getId().intValue())));
        perform(get("/api/analytics/reminders").param("assignee", "someone").with(authentication(ann))).andExpect(status().isBadRequest());

        submissions.submit(new SubmitReportRequest(ReportEntityType.FUNCTION, first.getId(), ReportCategory.OUTDATED_INFORMATION,
                "The fee listed here is no longer correct.", null, "en", null), "203.0.113.1");
        submissions.submit(new SubmitReportRequest(ReportEntityType.FUNCTION, third.getId(), ReportCategory.OUTDATED_INFORMATION,
                "The address listed here is no longer correct.", null, "en", null), "203.0.113.2");
        perform(get("/api/reports").param("assignee", "me").with(authentication(ann))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].entityId").value(first.getId()));
        perform(get("/api/reports").with(authentication(ann))).andExpect(jsonPath("$", hasSize(2)));
        perform(get("/api/analytics/summary").with(authentication(ann)))
                .andExpect(jsonPath("$.assignedToMe.services").value(1))
                .andExpect(jsonPath("$.assignedToMe.openReports").value(1))
                .andExpect(jsonPath("$.assignedToMe.openReminders").value(1));

        // The count is not capped by the length of the report list.
        Instant now = Instant.now();
        reportRows.saveAll(IntStream.range(0, ReportReviewService.LIST_LIMIT).mapToObj(index -> InformationReport.builder()
                .entityType(ReportEntityType.FUNCTION).entityId(first.getId()).entityLabel("First").organizationId(10L)
                .category(ReportCategory.OTHER).description("Report number " + index).createdAt(now).updatedAt(now).build())
                .toList());
        perform(get("/api/analytics/summary").with(authentication(ann)))
                .andExpect(jsonPath("$.assignedToMe.openReports").value(ReportReviewService.LIST_LIMIT + 1));
    }

    // ---- feedback --------------------------------------------------------------------------------------------------

    @Test void feedbackIsAggregatedPerCardScopedToOrganizationsAndRateLimited() throws Exception {
        OrgFunction own = published("Own", 10L);
        OrgFunction foreign = published("Foreign", 20L);
        OrgFunction draft = service.create(new CreateOrgFunctionRequest("Draft", "d", 10L, null, null));
        SecurityContextHolder.clearContext();

        for (String address : List.of("198.51.100.1", "198.51.100.2", "198.51.100.3")) {
            perform(post("/api/feedback").with(anonymous()).header("X-Real-IP", address).contentType("application/json")
                    .content("{\"serviceId\":" + own.getId() + ",\"helpful\":true}")).andExpect(status().isAccepted());
        }
        perform(post("/api/feedback").with(anonymous()).header("X-Real-IP", "198.51.100.1").contentType("application/json")
                .content("{\"serviceId\":" + own.getId() + ",\"helpful\":false}")).andExpect(status().isAccepted());
        perform(post("/api/feedback").with(anonymous()).header("X-Real-IP", "198.51.100.9").contentType("application/json")
                .content("{\"serviceId\":" + foreign.getId() + ",\"helpful\":false}")).andExpect(status().isAccepted());
        perform(post("/api/feedback").with(anonymous()).header("X-Real-IP", "198.51.100.9").contentType("application/json")
                .content("{\"serviceId\":" + draft.getId() + ",\"helpful\":true}")).andExpect(status().isNotFound());
        perform(post("/api/feedback").with(anonymous()).contentType("application/json")
                .content("{\"serviceId\":" + own.getId() + "}")).andExpect(status().isBadRequest());
        perform(post("/api/feedback").with(authentication(staff(8, List.of(10L), "FUNCTIONS_VIEW"))).contentType("application/json")
                .content("{\"serviceId\":" + own.getId() + ",\"helpful\":true}")).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT SUM(helpful_count) FROM feedback_daily_counts WHERE function_id = ?", Long.class, own.getId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT SUM(not_helpful_count) FROM feedback_daily_counts WHERE function_id = ?", Long.class, own.getId())).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM feedback_daily_counts").toString()).doesNotContain("198.51.100");

        perform(get("/api/analytics/feedback").with(authentication(staff(8, List.of(10L), "ORG_ANALYTICS_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.helpful").value(3))
                .andExpect(jsonPath("$.totals.notHelpful").value(1))
                .andExpect(jsonPath("$.totals.total").value(4))
                .andExpect(jsonPath("$.services", hasSize(1)))
                .andExpect(jsonPath("$.services[0].functionId").value(own.getId()));
        perform(get("/api/analytics/feedback").with(authentication(staff(9, List.of(20L), "ORG_ANALYTICS_VIEW"))))
                .andExpect(jsonPath("$.totals.total").value(1))
                .andExpect(jsonPath("$.services[0].functionId").value(foreign.getId()));
        perform(get("/api/analytics/feedback").param("organizationId", "10").with(authentication(staff(9, List.of(20L), "ORG_ANALYTICS_VIEW"))))
                .andExpect(status().isForbidden());
        perform(get("/api/analytics/feedback").with(authentication(superAdmin("ORG_ANALYTICS_VIEW"))))
                .andExpect(jsonPath("$.totals.total").value(5)).andExpect(jsonPath("$.services", hasSize(2)));
        perform(get("/api/analytics/feedback").with(authentication(staff(8, List.of(10L), "FUNCTIONS_VIEW")))).andExpect(status().isForbidden());
        perform(get("/api/analytics/feedback").with(anonymous())).andExpect(status().isForbidden());

        // 3 per client in the test configuration: the fourth vote from one address is throttled.
        for (int i = 0; i < 3; i++) {
            feedback.record(new FeedbackVoteRequest(own.getId(), true), "203.0.113.77");
        }
        perform(post("/api/feedback").with(anonymous()).header("X-Real-IP", "203.0.113.77").contentType("application/json")
                .content("{\"serviceId\":" + own.getId() + ",\"helpful\":true}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        assertThat(jdbc.queryForObject("SELECT SUM(helpful_count) FROM feedback_daily_counts WHERE function_id = ?", Long.class, own.getId())).isEqualTo(6);
    }

    private OrgFunction published(String name, Long organization) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        login(superAdmin(with(EDITOR)));
        try {
            OrgFunction card = service.create(new CreateOrgFunctionRequest(name, name + " description", organization, "Passport", null));
            service.submitForReview(card.getId());
            return service.publish(card.getId());
        } finally {
            SecurityContextHolder.getContext().setAuthentication(current);
        }
    }

    private ResultActions perform(RequestBuilder request) throws Exception {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        try {
            return mvc.perform(request);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(current);
        }
    }
}
