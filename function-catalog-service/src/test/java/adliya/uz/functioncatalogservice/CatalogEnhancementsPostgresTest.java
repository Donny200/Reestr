package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.*;
import adliya.uz.functioncatalogservice.entity.*;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.*;
import adliya.uz.functioncatalogservice.security.JwtPrincipal;
import adliya.uz.functioncatalogservice.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static adliya.uz.functioncatalogservice.entity.FunctionStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=enhancements-test-secret-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false",
        "catalog.reports.per-client-limit=1000", "catalog.reports.per-entity-daily-limit=3"
})
@AutoConfigureMockMvc
class CatalogEnhancementsPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    private static final String SOURCE = "https://gov.example/services/passport";

    @Autowired OrgFunctionService service;
    @Autowired FunctionVerificationService verification;
    @Autowired FunctionAutoTranslationService automatic;
    @Autowired FunctionCategoryService categories;
    @Autowired ReportSubmissionService submissions;
    @Autowired ReportReviewService reviews;
    @Autowired OrgFunctionRepository functions;
    @Autowired InformationReportRepository reports;
    @Autowired AuditLogRepository audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE information_reports, audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        login(List.of(10L), true, ALL);
        doAnswer(call -> {
            Long id = call.getArgument(0);
            if (id == null || (id != 10L && id != 20L)) throw new IllegalArgumentException("Unknown organization");
            return null;
        }).when(organizations).requireExisting(any());
        when(organizations.requirePublic(10L)).thenReturn(new IdentityOrganizationClient.PublicOrganization(10L, "Justice office"));
        when(organizations.requirePublic(99L)).thenThrow(new NoSuchElementException("Organization not found"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void structuredInstructionsPersistNormalizedAndKeepLegacyRequirementsAsFallback() throws Exception {
        var legacy = service.create(new CreateOrgFunctionRequest("Legacy", "Old card", 10L, "Passport, photo", null));
        var created = service.create(new CreateOrgFunctionRequest("Passport", "Issue a passport", 10L, "Bring originals", null, null, "en",
                new ServiceInstructions("Citizens aged 16+", "1. Apply\r\n\r\n2. Collect ", " Passport \n Photo ", "Any service centre", "", null),
                " " + SOURCE + " "));
        var stored = functions.findById(created.getId()).orElseThrow();
        assertThat(stored.getSteps()).isEqualTo("1. Apply\n2. Collect");
        assertThat(stored.getRequiredDocuments()).isEqualTo("Passport\nPhoto");
        assertThat(stored.getProcessingTime()).isNull();
        assertThat(stored.getFee()).isNull();
        assertThat(stored.getOfficialSourceUrl()).isEqualTo(SOURCE);
        assertThat(stored.getRequirements()).isEqualTo("Bring originals");
        var old = functions.findById(legacy.getId()).orElseThrow();
        assertThat(old.getRequirements()).isEqualTo("Passport, photo");
        assertThat(ServiceInstructions.of(old)).isEqualTo(new ServiceInstructions(null, null, null, null, null, null));

        service.submitForReview(created.getId());
        service.publish(created.getId());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/functions/{id}", created.getId()).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions.whoCanUse").value("Citizens aged 16+"))
                .andExpect(jsonPath("$.instructions.fee").doesNotExist())
                .andExpect(jsonPath("$.requirements").value("Bring originals"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
    }

    @Test void invalidOfficialSourceIsRejectedWithFieldError() throws Exception {
        String body = mapper.writeValueAsString(new CreateOrgFunctionRequest("Card", "Text", 10L, "", null, null, "en", null, "javascript:alert(1)"));
        mvc.perform(post("/api/functions").with(authentication(auth(List.of(10L), true, ALL))).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.officialSourceUrl").exists());
        assertThat(functions.count()).isZero();
    }

    @Test void verificationWorksWithoutAnExternalSourceRecordsReviewerAndCriticalEditsMarkItOutdated() {
        var card = service.create(new CreateOrgFunctionRequest("Card", "Text", 10L, "Docs", null));
        assertThat(card.getOfficialSourceUrl()).isNull();
        var verified = verification.verify(card.getId());
        assertThat(verified.getVerifiedByUserId()).isEqualTo(42L);
        assertThat(verified.getLastVerifiedAt()).isNotNull();
        assertThat(verified.verificationStatus(java.time.Instant.now())).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(service.history(card.getId())).extracting(AuditLogResponse::details)
                .anyMatch(details -> details.startsWith("Verified by organization staff") && details.contains("no external source link"));
        service.update(card.getId(), update(null, SOURCE));
        verification.verify(card.getId());

        var category = categories.create(new FunctionCategoryRequest("Docs", null));
        service.update(card.getId(), new UpdateOrgFunctionRequest(null, null, null, null, null, category.getId(), null));
        service.updateLanguageTranslation(card.getId(), "ru", new LanguageTranslationRequest("Карточка", "Текст"));
        assertThat(functions.findById(card.getId()).orElseThrow().isVerificationOutdated()).isFalse();

        service.update(card.getId(), update(new ServiceInstructions(null, null, null, null, null, "10 000 UZS"), null));
        var changed = functions.findById(card.getId()).orElseThrow();
        assertThat(changed.verificationStatus(java.time.Instant.now())).isEqualTo(VerificationStatus.OUTDATED);
        assertThat(service.history(card.getId())).extracting(AuditLogResponse::details)
                .anyMatch(details -> details.contains("fee") && details.contains("verification outdated"))
                .anyMatch(details -> details.startsWith("Verified by organization staff") && details.contains(SOURCE));

        verification.verify(card.getId());
        assertThat(functions.findById(card.getId()).orElseThrow().isVerificationOutdated()).isFalse();
        service.updateRequirements(card.getId(), "New requirement text");
        assertThat(functions.findById(card.getId()).orElseThrow().isVerificationOutdated()).isTrue();
    }

    @Test void verificationIsScopedAndPublishedCardsCanBeRechecked() throws Exception {
        var foreign = service.create(new CreateOrgFunctionRequest("Foreign", "Text", 20L, "", null, null, "en", null, SOURCE));
        login(List.of(10L), false, ALL);
        assertThatThrownBy(() -> verification.verify(foreign.getId())).isInstanceOf(AccessDeniedException.class);
        login(List.of(10L), true, ALL);
        service.submitForReview(foreign.getId());
        service.publish(foreign.getId());
        mvc.perform(post("/api/functions/{id}/verify", foreign.getId()).with(authentication(auth(List.of(20L), false, List.of("FUNCTIONS_REVIEW")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/functions/{id}", foreign.getId()).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.lastVerifiedAt").exists())
                .andExpect(jsonPath("$.verifiedByUserId").doesNotExist());
    }

    @Test void instructionTranslationsKeepSourcesAndFollowOriginalChanges() {
        var card = service.create(new CreateOrgFunctionRequest("Card", "Text", 10L, "", null, null, "en",
                new ServiceInstructions("Citizens", "Apply\nCollect", null, null, null, null), null));
        service.updateLanguageTranslation(card.getId(), "ru", new LanguageTranslationRequest("Карточка", "Текст",
                Map.of("whoCanUse", "Граждане", "steps", "Подать\nПолучить")));
        assertThatThrownBy(() -> service.updateLanguageTranslation(card.getId(), "ru",
                new LanguageTranslationRequest("Карточка", "Текст", Map.of("fee", "Бесплатно"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fee");
        assertThatThrownBy(() -> service.updateLanguageTranslation(card.getId(), "ru",
                new LanguageTranslationRequest("Карточка", "Текст", Map.of("unknown", "x"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown");

        when(translator.isAvailable()).thenReturn(true);
        when(translator.translateRequired(any(), eq("en"), any())).thenAnswer(call -> Map.of("uz", "Mashina: " + call.getArgument(0)));
        automatic.translate(card.getId(), new AutoTranslateRequest(List.of("ru", "uz"), false));
        var translated = functions.findById(card.getId()).orElseThrow();
        assertThat(translated.instructionTranslationsOf(InstructionField.WHO_CAN_USE))
                .containsEntry("ru", new TranslatedText("Граждане", TranslatedText.HUMAN))
                .containsEntry("uz", new TranslatedText("Mashina: Citizens", TranslatedText.MACHINE));
        assertThat(translated.instructionTranslationsOf(InstructionField.STEPS).get("uz").text()).isEqualTo("Mashina: Apply\nCollect");

        service.update(card.getId(), update(new ServiceInstructions("Residents", null, null, null, null, null), null));
        var updated = functions.findById(card.getId()).orElseThrow();
        assertThat(updated.instructionTranslationsOf(InstructionField.WHO_CAN_USE))
                .containsOnlyKeys("ru").containsEntry("ru", new TranslatedText("Граждане", TranslatedText.HUMAN));
        assertThat(updated.getSteps()).isNull();
        assertThat(updated.instructionTranslationsOf(InstructionField.STEPS)).isEmpty();
    }

    @Test void anonymousReportIsStoredPrivatelyAndNeverChangesPublicContent() throws Exception {
        var card = published("Passport renewal", 10L);
        long version = functions.findById(card.getId()).orElseThrow().getVersion();
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/reports").with(anonymous()).header("X-Real-IP", "203.0.113.10").contentType("application/json").content("""
                        {"entityType":"FUNCTION","entityId":%d,"category":"FEES_OR_TIMING",
                         "description":"  The fee   shown here\\r\\nis outdated.  ","contact":" visitor@example.uz ","language":"RU"}
                        """.formatted(card.getId())))
                .andExpect(status().isAccepted());
        var report = reports.findAll().get(0);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.NEW);
        assertThat(report.getOrganizationId()).isEqualTo(10L);
        assertThat(report.getEntityLabel()).isEqualTo("Passport renewal");
        assertThat(report.getDescription()).isEqualTo("The fee shown here\nis outdated.");
        assertThat(report.getContact()).isEqualTo("visitor@example.uz");
        assertThat(report.getLanguage()).isEqualTo("ru");
        assertThat(functions.findById(card.getId()).orElseThrow().getVersion()).isEqualTo(version);
        assertThat(audit.findAll()).filteredOn(log -> log.getAction() == AuditAction.REPORT_RECEIVED).singleElement()
                .satisfies(log -> {
                    assertThat(log.getDetails()).doesNotContain("visitor@example.uz").doesNotContain("outdated");
                    assertThat(log.getPerformedBy()).isEqualTo("public:anonymous");
                });
        mvc.perform(get("/api/reports").with(anonymous())).andExpect(status().isForbidden());
        mvc.perform(get("/api/functions/{id}", card.getId()).with(anonymous())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("visitor@example.uz"))));
    }

    @Test void submissionsOnlyTargetPublicContentAndResistSpam() {
        var draft = service.create(new CreateOrgFunctionRequest("Draft", "Text", 10L, "", null));
        var card = published("Visible", 10L);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> submissions.submit(report(ReportEntityType.FUNCTION, draft.getId(), ReportCategory.OTHER, "Draft is wrong!!", null), "a"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> submissions.submit(report(ReportEntityType.ORGANIZATION, 99L, ReportCategory.OTHER, "Unknown organization", null), "a"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.CONTACT_DETAILS, "Wrong category", null), "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER, "!!!!!!!!!!!!", null), "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER,
                "http://a http://b http://c http://d", null), "a")).isInstanceOf(IllegalArgumentException.class);

        submissions.submit(new SubmitReportRequest(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER,
                "Bot filled the hidden field", null, null, "https://spam.example"), "bot");
        submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER, "Same problem text", null), "a");
        submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER, "Same  problem text", null), "b");
        assertThat(reports.count()).isEqualTo(1);
        submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER, "Second problem text", null), "c");
        submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER, "Third problem text", null), "d");
        assertThatThrownBy(() -> submissions.submit(report(ReportEntityType.FUNCTION, card.getId(), ReportCategory.OTHER, "Fourth problem text", null), "e"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(reports.count()).isEqualTo(3);
    }

    @Test void reviewIsScopedPermissionCheckedAuditedAndErasesContactOnClose() throws Exception {
        var own = published("Own", 10L);
        var foreign = published("Foreign", 20L);
        SecurityContextHolder.clearContext();
        submissions.submit(report(ReportEntityType.FUNCTION, own.getId(), ReportCategory.OTHER, "Own card problem", "+998 71 200-00-00"), "a");
        submissions.submit(report(ReportEntityType.FUNCTION, foreign.getId(), ReportCategory.OTHER, "Foreign card problem", null), "b");
        submissions.submit(report(ReportEntityType.ORGANIZATION, 10L, ReportCategory.CONTACT_DETAILS, "Phone number is wrong", null), "c");
        var ownReport = reports.findAll().stream().filter(item -> item.getEntityId().equals(own.getId())).findFirst().orElseThrow();
        var foreignReport = reports.findAll().stream().filter(item -> item.getEntityId().equals(foreign.getId())).findFirst().orElseThrow();

        var viewer = auth(List.of(10L), false, List.of("REPORTS_VIEW"));
        mvc.perform(get("/api/reports").with(authentication(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.entityId == %d && @.entityType == 'FUNCTION')]".formatted(foreign.getId())).isEmpty());
        mvc.perform(get("/api/reports/{id}", foreignReport.getId()).with(authentication(viewer))).andExpect(status().isForbidden());
        mvc.perform(put("/api/reports/{id}/status", ownReport.getId()).with(authentication(viewer))
                .contentType("application/json").content("{\"status\":\"RESOLVED\"}")).andExpect(status().isForbidden());

        var manager = auth(List.of(10L), false, List.of("REPORTS_VIEW", "REPORTS_MANAGE"));
        mvc.perform(put("/api/reports/{id}/status", foreignReport.getId()).with(authentication(manager))
                .contentType("application/json").content("{\"status\":\"RESOLVED\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/reports/{id}/status", ownReport.getId()).with(authentication(manager))
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contact").value("+998 71 200-00-00"));
        mvc.perform(put("/api/reports/{id}/status", ownReport.getId()).with(authentication(manager))
                        .contentType("application/json").content("{\"status\":\"RESOLVED\",\"note\":\"Fee corrected in the next revision\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.handledByUserId").value(42));
        mvc.perform(put("/api/reports/{id}/status", ownReport.getId()).with(authentication(manager))
                .contentType("application/json").content("{\"status\":\"REJECTED\",\"note\":\"Duplicate\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/reports?status=RESOLVED").with(authentication(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/reports?status=bogus").with(authentication(viewer))).andExpect(status().isBadRequest());

        login(List.of(10L), true, ALL);
        assertThat(service.history(own.getId())).extracting(AuditLogResponse::action)
                .contains(AuditAction.REPORT_RECEIVED, AuditAction.REPORT_STATUS_CHANGE);
        assertThat(service.getById(own.getId()).getStatus()).isEqualTo(PUBLISHED);
        assertThat(reviews.list(new ReportQuery(ReportQuery.statuses("all"), null, null, null, null, null, null, null))).hasSize(3);
    }

    private OrgFunction published(String name, Long organization) {
        login(List.of(10L, 20L), true, ALL);
        var card = service.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null));
        service.submitForReview(card.getId());
        return service.publish(card.getId());
    }
    private static SubmitReportRequest report(ReportEntityType type, Long id, ReportCategory category, String description, String contact) {
        return new SubmitReportRequest(type, id, category, description, contact, "en", null);
    }
    private static UpdateOrgFunctionRequest update(ServiceInstructions instructions, String source) {
        return new UpdateOrgFunctionRequest(null, null, null, null, null, null, null, instructions, source);
    }
    private static final List<String> ALL = List.of("FUNCTIONS_CREATE", "FUNCTIONS_EDIT", "FUNCTIONS_MANAGE_REQUIREMENTS",
            "FUNCTIONS_DEACTIVATE", "FUNCTIONS_SUBMIT_REVIEW", "FUNCTIONS_REVIEW", "FUNCTIONS_PUBLISH", "FUNCTIONS_REACTIVATE",
            "FUNCTIONS_TRANSLATIONS_EDIT", "FUNCTION_CATEGORIES_MANAGE", "AUDIT_VIEW", "FUNCTIONS_VIEW", "REPORTS_VIEW", "REPORTS_MANAGE");
    private static UsernamePasswordAuthenticationToken auth(List<Long> organizationIds, boolean global, List<String> permissions) {
        var principal = new JwtPrincipal("editor@example.com", global ? "ROLE_SUPER_ADMIN" : "ROLE_CUSTOM", organizationIds, permissions, 42L);
        return new UsernamePasswordAuthenticationToken(principal, null, permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }
    private static void login(List<Long> organizationIds, boolean global, List<String> permissions) {
        SecurityContextHolder.getContext().setAuthentication(auth(organizationIds, global, permissions));
    }
}
