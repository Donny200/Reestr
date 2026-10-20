package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.AuditLogResponse;
import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.LanguageTranslationRequest;
import adliya.uz.functioncatalogservice.dto.QualityIssue;
import adliya.uz.functioncatalogservice.dto.ServiceInstructions;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.entity.VerificationStatus;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.AuditLogRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.JwtPrincipal;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import adliya.uz.functioncatalogservice.service.ActiveLanguageClient;
import adliya.uz.functioncatalogservice.service.FunctionVerificationService;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.QualityReminderService;
import adliya.uz.functioncatalogservice.service.ServiceQualityService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.core.Authentication;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static adliya.uz.functioncatalogservice.entity.FunctionStatus.DEACTIVATED;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.DRAFT;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.PENDING_REVIEW;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Draft revisions of published cards: the public version never changes until a reviewer publishes the revision. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=revision-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false"
})
@AutoConfigureMockMvc
class FunctionRevisionPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final List<String> ALL = List.of("FUNCTIONS_VIEW", "FUNCTIONS_CREATE", "FUNCTIONS_EDIT", "FUNCTIONS_MANAGE_REQUIREMENTS",
            "FUNCTIONS_DEACTIVATE", "FUNCTIONS_SUBMIT_REVIEW", "FUNCTIONS_REVIEW", "FUNCTIONS_PUBLISH", "FUNCTIONS_REACTIVATE",
            "FUNCTIONS_TRANSLATIONS_EDIT", "AUDIT_VIEW", "ORG_ANALYTICS_VIEW");

    @Autowired OrgFunctionService service;
    @Autowired FunctionVerificationService verification;
    @Autowired ServiceQualityService quality;
    @Autowired QualityReminderService reminders;
    @Autowired OrgFunctionRepository functions;
    @Autowired AuditLogRepository audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean ActiveLanguageClient languages;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE quality_reminders, information_reports, audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        login(List.of(10L), true, ALL);
        doAnswer(call -> {
            Long id = call.getArgument(0);
            if (id == null || (id != 10L && id != 20L)) throw new IllegalArgumentException("Unknown organization");
            return null;
        }).when(organizations).requireExisting(any());
        when(languages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "ru")));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void publishedContentStaysPublicWhileTheRevisionIsEditedAndReviewedThenReplacesIt() throws Exception {
        OrgFunction card = published("Passport", "Old public text", 10L);
        verification.verify(card.getId());
        Long id = card.getId();

        OrgFunction revision = service.createRevision(id);
        assertThat(revision.getId()).isNotEqualTo(id);
        assertThat(revision.getStatus()).isEqualTo(DRAFT);
        assertThat(revision.getRevisionOfId()).isEqualTo(id);
        assertThat(revision.getDescription()).isEqualTo("Old public text");
        assertThat(revision.getLastVerifiedAt()).isNotNull();
        assertThat(service.createRevision(id).getId()).isEqualTo(revision.getId());

        service.update(revision.getId(), new UpdateOrgFunctionRequest("Passport renewal", "New public text", null, null, null, null, null,
                new ServiceInstructions(null, null, null, null, null, "10 000 UZS"), null));
        service.updateLanguageTranslation(revision.getId(), "ru", new LanguageTranslationRequest("Продление паспорта", "Новый текст"));
        assertThatThrownBy(() -> service.update(id, new UpdateOrgFunctionRequest("Direct edit", null, null, null, null, null, null)))
                .isInstanceOf(WorkflowConflictException.class);
        expectPublic(id, "Passport", "Old public text");
        perform(get("/api/functions/{id}", revision.getId()).with(anonymous())).andExpect(status().isNotFound());

        service.submitForReview(revision.getId());
        assertThat(service.pendingReview()).extracting(OrgFunction::getId).containsExactly(revision.getId());
        perform(get("/api/functions/{id}/admin", id).with(authentication(auth(List.of(10L), true, ALL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.description").value("Old public text"))
                .andExpect(jsonPath("$.revision.id").value(revision.getId()))
                .andExpect(jsonPath("$.revision.status").value("PENDING_REVIEW"));
        assertThatThrownBy(() -> service.updateRequirements(revision.getId(), "Locked")).isInstanceOf(WorkflowConflictException.class);
        expectPublic(id, "Passport", "Old public text");

        service.withdraw(revision.getId());
        assertThat(functions.findById(revision.getId()).orElseThrow().getStatus()).isEqualTo(DRAFT);
        service.submitForReview(revision.getId());
        service.reject(revision.getId(), "Check the fee");
        service.submitForReview(revision.getId());
        expectPublic(id, "Passport", "Old public text");

        OrgFunction result = service.publish(revision.getId());
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getStatus()).isEqualTo(PUBLISHED);
        assertThat(functions.existsById(revision.getId())).isFalse();
        assertThat(functions.findByRevisionOfId(id)).isEmpty();
        expectPublic(id, "Passport renewal", "New public text");
        perform(get("/api/functions/{id}", id).with(anonymous()))
                .andExpect(jsonPath("$.instructions.fee").value("10 000 UZS"))
                .andExpect(jsonPath("$.nameTranslations.ru.text").value("Продление паспорта"))
                .andExpect(jsonPath("$.verificationStatus").value("OUTDATED"));
        assertThat(service.getAllForAdmin()).extracting(OrgFunction::getId).containsExactly(id);

        List<AuditAction> history = service.history(id).stream().map(AuditLogResponse::action).toList();
        assertThat(history).containsSubsequence(AuditAction.PUBLISH, AuditAction.VERIFY, AuditAction.REVISION_CREATE, AuditAction.UPDATE,
                AuditAction.TRANSLATION_EDIT, AuditAction.SUBMIT_REVIEW, AuditAction.WITHDRAW, AuditAction.SUBMIT_REVIEW,
                AuditAction.REJECT, AuditAction.SUBMIT_REVIEW, AuditAction.PUBLISH);
        assertThat(service.history(id)).extracting(AuditLogResponse::details).anyMatch(details -> details.contains("Revision #" + revision.getId() + " approved"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log_functions WHERE function_id = ?", Long.class, revision.getId())).isZero();
    }

    @Test void discardingTheRevisionLeavesThePublishedCardAndItsHistoryIntact() throws Exception {
        OrgFunction card = published("Licence", "Public", 10L);
        OrgFunction revision = service.createRevision(card.getId());
        service.update(revision.getId(), new UpdateOrgFunctionRequest("Changed", "Changed text", null, null, null, null, null));
        service.submitForReview(revision.getId());
        assertThatThrownBy(() -> service.discardRevision(card.getId())).isInstanceOf(WorkflowConflictException.class);
        service.withdraw(revision.getId());

        perform(delete("/api/functions/{id}/revision", card.getId()).with(authentication(auth(List.of(10L), false, ALL))))
                .andExpect(status().isNoContent());

        assertThat(functions.existsById(revision.getId())).isFalse();
        expectPublic(card.getId(), "Licence", "Public");
        assertThat(service.history(card.getId())).extracting(AuditLogResponse::action)
                .contains(AuditAction.REVISION_CREATE, AuditAction.UPDATE, AuditAction.SUBMIT_REVIEW, AuditAction.WITHDRAW, AuditAction.REVISION_DISCARD);
        assertThat(audit.findFunctionHistory(revision.getId())).isEmpty();
    }

    @Test void deactivatedCardIsEditedInPlaceAndPublishedAgainAfterReview() throws Exception {
        OrgFunction card = published("Archive", "Public", 10L);
        service.deactivate(card.getId());
        perform(get("/api/functions/{id}", card.getId()).with(anonymous())).andExpect(status().isNotFound());
        assertThatThrownBy(() -> service.createRevision(card.getId())).isInstanceOf(WorkflowConflictException.class);

        service.updateRequirements(card.getId(), "Updated documents");
        service.update(card.getId(), new UpdateOrgFunctionRequest(null, "Corrected text", null, null, null, null, null));
        service.submitForReview(card.getId());
        assertThat(functions.findById(card.getId()).orElseThrow().getStatus()).isEqualTo(PENDING_REVIEW);
        perform(get("/api/functions/{id}", card.getId()).with(anonymous())).andExpect(status().isNotFound());
        service.publish(card.getId());
        expectPublic(card.getId(), "Archive", "Corrected text");
        assertThat(service.history(card.getId())).extracting(AuditLogResponse::details).contains("DEACTIVATED -> PENDING_REVIEW");
    }

    @Test void organizationStaffEditOnlyTheirOwnCardsWhileSuperAdminEditsAcrossOrganizations() throws Exception {
        OrgFunction own = published("Own", "Text", 10L);
        OrgFunction foreign = published("Foreign", "Text", 20L);
        var staff = auth(List.of(10L), false, ALL);
        perform(post("/api/functions/{id}/revisions", foreign.getId()).with(authentication(staff))).andExpect(status().isForbidden());
        perform(post("/api/functions/{id}/revisions", own.getId()).with(authentication(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revisionOfId").value(own.getId()));
        Long ownRevision = functions.findByRevisionOfId(own.getId()).orElseThrow().getId();
        perform(put("/api/functions/{id}", ownRevision).with(authentication(staff)).contentType("application/json")
                        .content("{\"name\":\"Own edited\"}")).andExpect(status().isOk());
        login(List.of(10L), false, ALL);
        assertThatThrownBy(() -> service.createRevision(foreign.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.withdraw(foreign.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(functions.findByRevisionOfId(foreign.getId())).isEmpty();

        var superAdmin = auth(List.of(), true, List.of("FUNCTIONS_EDIT", "FUNCTIONS_SUBMIT_REVIEW", "FUNCTIONS_PUBLISH", "AUDIT_VIEW"));
        perform(post("/api/functions/{id}/revisions", foreign.getId()).with(authentication(superAdmin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.organizationId").value(20));
        Long foreignRevision = functions.findByRevisionOfId(foreign.getId()).orElseThrow().getId();
        perform(put("/api/functions/{id}", foreignRevision).with(authentication(superAdmin)).contentType("application/json")
                .content("{\"description\":\"Edited by super admin\"}")).andExpect(status().isOk());
        perform(post("/api/functions/{id}/submit-for-review", foreignRevision).with(authentication(superAdmin))).andExpect(status().isOk());
        perform(post("/api/functions/{id}/publish", foreignRevision).with(authentication(superAdmin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(foreign.getId()));
        expectPublic(foreign.getId(), "Foreign", "Edited by super admin");
        login(List.of(), true, ALL);
        assertThat(service.history(foreign.getId())).allSatisfy(event -> assertThat(event.performedByUserId()).isEqualTo(42L));
    }

    @Test void revisionRowsStayOutOfTheQualityQueueDashboardAndReminders() {
        OrgFunction card = published("Quality", "Text", 10L);
        OrgFunction revision = service.createRevision(card.getId());
        service.update(revision.getId(), new UpdateOrgFunctionRequest(null, "   ", null, null, null, null, null));

        ServiceQualityService.Snapshot snapshot = quality.snapshot(OrganizationScope.all(), null);
        assertThat(snapshot.statuses()).containsEntry(FunctionStatus.PUBLISHED, 1L).containsEntry(DRAFT, 0L);
        assertThat(snapshot.functionIds()).containsExactly(card.getId());
        assertThat(quality.queue(null, null, null, null).items()).extracting(item -> item.functionId()).doesNotContain(revision.getId());
        reminders.scan(Instant.now());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quality_reminders WHERE function_id = ?", Long.class, revision.getId())).isZero();
    }

    @Test void missingExternalSourceLinkNeverBlocksSavingReviewVerificationOrPublication() throws Exception {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("No link", "Text", 10L, "Docs", null));
        service.update(card.getId(), new UpdateOrgFunctionRequest(null, null, null, null, null, null, null, null, ""));
        service.submitForReview(card.getId());
        service.publish(card.getId());
        verification.verify(card.getId());
        OrgFunction stored = functions.findById(card.getId()).orElseThrow();
        assertThat(stored.getOfficialSourceUrl()).isNull();
        assertThat(stored.verificationStatus(Instant.now())).isEqualTo(VerificationStatus.VERIFIED);
        perform(get("/api/functions/{id}", card.getId()).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.officialSourceUrl").isEmpty())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        assertThat(quality.queue(null, null, QualityIssueType.SOURCE_MISSING, null).items()).isEmpty();
        assertThat(quality.queue(null, null, null, null).items()).noneMatch(item -> item.functionId().equals(card.getId())
                && item.issues().stream().map(QualityIssue::type).anyMatch(type -> type == QualityIssueType.SOURCE_MISSING));
        assertThat(service.history(card.getId())).extracting(AuditLogResponse::details)
                .anyMatch(details -> details.startsWith("Verified by organization staff"));
    }

    /** The security filter chain clears the thread's authentication after each request; keep the test's login. */
    private ResultActions perform(RequestBuilder request) throws Exception {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        try {
            return mvc.perform(request);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(current);
        }
    }

    private OrgFunction published(String name, String description, Long organization) {
        OrgFunction card = service.create(new CreateOrgFunctionRequest(name, description, organization, "Docs", null));
        service.submitForReview(card.getId());
        return service.publish(card.getId());
    }

    private void expectPublic(Long id, String name, String description) throws Exception {
        perform(get("/api/functions/{id}", id).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.revisionOfId").doesNotExist());
        perform(get("/api/functions").with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].description").value(description));
    }

    private static UsernamePasswordAuthenticationToken auth(List<Long> organizationIds, boolean global, List<String> permissions) {
        var principal = new JwtPrincipal("editor@example.com", global ? "ROLE_SUPER_ADMIN" : "ROLE_CUSTOM", organizationIds, permissions, 42L);
        return new UsernamePasswordAuthenticationToken(principal, null, permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static void login(List<Long> organizationIds, boolean global, List<String> permissions) {
        SecurityContextHolder.getContext().setAuthentication(auth(organizationIds, global, permissions));
    }
}
