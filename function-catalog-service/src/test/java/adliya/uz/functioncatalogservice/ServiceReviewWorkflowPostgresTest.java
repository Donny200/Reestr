package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.AuditLogResponse;
import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.FunctionReviewResponse;
import adliya.uz.functioncatalogservice.dto.ServiceInstructions;
import adliya.uz.functioncatalogservice.dto.ServiceProfile;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.DeliveryChannel;
import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ProvisionLevel;
import adliya.uz.functioncatalogservice.entity.ReviewCommentStatus;
import adliya.uz.functioncatalogservice.entity.ReviewResolution;
import adliya.uz.functioncatalogservice.entity.RiskLevel;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.JwtPrincipal;
import adliya.uz.functioncatalogservice.service.ActiveLanguageClient;
import adliya.uz.functioncatalogservice.service.FunctionReviewService;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static adliya.uz.functioncatalogservice.entity.FunctionStatus.CHANGES_REQUESTED;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Multi-step editor data and field-level review: corrections, changes requested, resubmission and approval. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=review-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false"
})
@AutoConfigureMockMvc
class ServiceReviewWorkflowPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final List<String> AUTHOR = List.of("FUNCTIONS_VIEW", "FUNCTIONS_CREATE", "FUNCTIONS_EDIT", "FUNCTIONS_SUBMIT_REVIEW",
            "AUDIT_VIEW");
    private static final List<String> REVIEWER = List.of("FUNCTIONS_VIEW", "FUNCTIONS_REVIEW", "AUDIT_VIEW");
    private static final List<String> PUBLISHER = List.of("FUNCTIONS_VIEW", "FUNCTIONS_REVIEW", "FUNCTIONS_PUBLISH", "AUDIT_VIEW");

    @Autowired OrgFunctionService service;
    @Autowired FunctionReviewService review;
    @Autowired OrgFunctionRepository functions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean ActiveLanguageClient languages;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE function_review_marks, function_review_comments, quality_reminders, information_reports, "
                + "audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> {
            Long id = call.getArgument(0);
            if (id == null || (id != 10L && id != 20L)) throw new IllegalArgumentException("Unknown organization");
            return null;
        }).when(organizations).requireExisting(any());
        when(languages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "ru")));
        login(1L, AUTHOR);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void draftKeepsEveryStepAndTheStepItWasSavedFromWhileThePublicNeverSeesTheProfile() throws Exception {
        OrgFunction draft = service.create(new CreateOrgFunctionRequest("Passport", "Issue a passport", 10L, "Bring the old one", null, null,
                "en", new ServiceInstructions("Citizens", "Apply\nCollect", "Application\nPhoto", "At the service centre", "5 days", "Free"),
                null, null, new ServiceProfile(DeliveryChannel.ONLINE_AND_IN_PERSON, "https://my.gov.example/passport",
                ProvisionLevel.REGIONAL, RiskLevel.MEDIUM, "Manual identity check"), "digitalization"));

        perform(get("/api/functions/{id}/admin", draft.getId()).with(authentication(auth(1L, AUTHOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editorStep").value("digitalization"))
                .andExpect(jsonPath("$.profile.deliveryChannel").value("ONLINE_AND_IN_PERSON"))
                .andExpect(jsonPath("$.profile.onlineServiceUrl").value("https://my.gov.example/passport"))
                .andExpect(jsonPath("$.profile.riskNotes").value("Manual identity check"))
                .andExpect(jsonPath("$.instructions.processingTime").value("5 days"))
                .andExpect(jsonPath("$.review.cycle").value(0));

        // Saving from a later step only moves the resume point; the content and its audit history stay as they were.
        service.update(draft.getId(), new UpdateOrgFunctionRequest(null, null, null, null, null, null, null, null, null, null, null, "other"));
        OrgFunction stored = functions.findById(draft.getId()).orElseThrow();
        assertThat(stored.getEditorStep()).isEqualTo("other");
        assertThat(stored.getProvisionLevel()).isEqualTo(ProvisionLevel.REGIONAL);
        assertThat(service.history(draft.getId())).extracting(AuditLogResponse::details).containsExactly("Draft created");

        publishAsReviewer(draft.getId());
        SecurityContextHolder.clearContext();
        perform(get("/api/functions/{id}", draft.getId()).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Passport"))
                .andExpect(jsonPath("$.profile").doesNotExist())
                .andExpect(jsonPath("$.editorStep").doesNotExist())
                .andExpect(jsonPath("$.review").doesNotExist());
        perform(get("/api/functions/{id}/review", draft.getId()).with(anonymous())).andExpect(status().is4xxClientError());
    }

    @Test void submissionRequiresTheConditionalFieldsOfEveryStep() throws Exception {
        OrgFunction draft = service.create(new CreateOrgFunctionRequest("Licence", "Issue a licence", 10L, null, null, null, "en",
                null, null, null, new ServiceProfile(DeliveryChannel.ONLINE, null, null, RiskLevel.HIGH, " "), null));
        perform(post("/api/functions/{id}/submit-for-review", draft.getId()).with(authentication(auth(1L, AUTHOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['profile.onlineServiceUrl']").exists())
                .andExpect(jsonPath("$.fieldErrors['profile.riskNotes']").exists());
        assertThat(functions.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(DRAFT);

        perform(put("/api/functions/{id}", draft.getId()).with(authentication(auth(1L, AUTHOR))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"profile\":{\"deliveryChannel\":\"ONLINE\",\"onlineServiceUrl\":\"not a link\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['profile.onlineServiceUrl']").exists());

        service.update(draft.getId(), new UpdateOrgFunctionRequest(null, null, null, null, null, null, null, null, null, null,
                new ServiceProfile(DeliveryChannel.ONLINE, "https://my.gov.example/licence", null, RiskLevel.HIGH, "Cash handling"), null));
        assertThat(service.submitForReview(draft.getId()).getStatus()).isEqualTo(PENDING_REVIEW);
    }

    @Test void fieldCorrectionsReturnTheServiceForChangesAndBlockApprovalUntilAddressed() throws Exception {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Pension", "Old-age pension", 10L, null, null, null, "en",
                new ServiceInstructions(null, null, "Passport\nWork record", null, null, "100 UZS"), null,
                List.of(new DocumentDetail("Work record", DocumentDetail.Form.COPY, 2, null, null, null)), null, "general"));
        String documentId = functions.findById(card.getId()).orElseThrow().getDocumentDetails().get(0).id();
        service.submitForReview(card.getId());
        Long id = card.getId();

        // Only reviewers flag fields, and only with an explanation, on a known field, once per field.
        perform(post("/api/functions/{id}/review/comments", id).with(authentication(auth(1L, AUTHOR)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fieldPath\":\"instructions.fee\",\"comment\":\"Fee is outdated\"}"))
                .andExpect(status().isForbidden());
        login(2L, REVIEWER);
        perform(post("/api/functions/{id}/review/comments", id).with(authentication(auth(2L, REVIEWER)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fieldPath\":\"instructions.fee\",\"comment\":\"   \"}"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> review.addComment(id, "instructions.unknown", "Check")).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> review.addComment(id, "documentDetails.no-such-item", "Check")).isInstanceOf(InvalidFieldException.class);
        perform(post("/api/functions/{id}/review/comments", id).with(authentication(auth(2L, REVIEWER)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fieldPath\":\"instructions.fee\",\"comment\":\"The fee changed in 2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].fieldPath").value("instructions.fee"))
                .andExpect(jsonPath("$.comments[0].section").value("mechanism"))
                .andExpect(jsonPath("$.comments[0].reviewer").value("staff2@example.test"))
                .andExpect(jsonPath("$.comments[0].reviewCycle").value(1));
        assertThatThrownBy(() -> review.addComment(id, "instructions.fee", "Again")).isInstanceOf(WorkflowConflictException.class);
        review.addComment(id, "documentDetails." + documentId, "Two copies are not needed");
        review.markSection(id, "general", true);

        // A publisher cannot approve while corrections are open; the response names the affected fields.
        perform(post("/api/functions/{id}/publish", id).with(authentication(auth(3L, PUBLISHER))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("instructions.fee")));
        perform(post("/api/functions/{id}/publish", id).with(authentication(auth(2L, REVIEWER)))).andExpect(status().isForbidden());

        perform(post("/api/functions/{id}/request-changes", id).with(authentication(auth(2L, REVIEWER)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"Please update the fee and documents\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"));
        perform(post("/api/functions/{id}/request-changes", id).with(authentication(auth(2L, REVIEWER)))).andExpect(status().isConflict());

        // The author sees the open notes and can edit; resubmitting an unchanged field is refused.
        login(1L, AUTHOR);
        FunctionReviewResponse returned = review.review(id);
        assertThat(returned.status()).isEqualTo(CHANGES_REQUESTED);
        assertThat(returned.unaddressedFields()).containsExactly("instructions.fee", "documentDetails." + documentId);
        assertThat(returned.canResubmit()).isFalse();
        assertThatThrownBy(() -> service.submitForReview(id)).isInstanceOf(WorkflowConflictException.class)
                .hasMessageContaining("instructions.fee");

        // Reordering the document list keeps the note on the same item (stable id), so it still needs a change.
        service.update(id, new UpdateOrgFunctionRequest(null, null, null, null, null, null, null,
                new ServiceInstructions(null, null, "Work record\nPassport", null, null, "120 UZS"), null, null));
        assertThat(review.review(id).unaddressedFields()).containsExactly("documentDetails." + documentId);
        service.update(id, new UpdateOrgFunctionRequest(null, null, null, null, null, null, null, null, null,
                List.of(new DocumentDetail("Work record", DocumentDetail.Form.COPY, 1, null, null, null, documentId))));
        FunctionReviewResponse ready = review.review(id);
        assertThat(ready.canResubmit()).isTrue();
        assertThat(ready.comments()).allSatisfy(comment -> assertThat(comment.addressed()).isTrue());

        OrgFunction resubmitted = service.submitForReview(id);
        assertThat(resubmitted.getStatus()).isEqualTo(PENDING_REVIEW);
        assertThat(resubmitted.getReviewCycle()).isEqualTo(2);
        FunctionReviewResponse second = review.review(id);
        assertThat(second.comments()).hasSize(2).allSatisfy(comment -> {
            assertThat(comment.status()).isEqualTo(ReviewCommentStatus.RESOLVED);
            assertThat(comment.resolution()).isEqualTo(ReviewResolution.ADDRESSED);
            assertThat(comment.resolvedInCycle()).isEqualTo(2);
            assertThat(comment.reviewCycle()).isEqualTo(1);
        });
        assertThat(second.reviewedSections()).isEmpty();

        // New cycle: an approval by an authorized publisher publishes and is audited.
        login(3L, PUBLISHER);
        perform(post("/api/functions/{id}/publish", id).with(authentication(auth(3L, PUBLISHER)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(service.history(id)).extracting(AuditLogResponse::action).containsSubsequence(
                AuditAction.SUBMIT_REVIEW, AuditAction.REVIEW_COMMENT_ADD, AuditAction.REVIEW_COMMENT_ADD, AuditAction.REQUEST_CHANGES,
                AuditAction.SUBMIT_REVIEW, AuditAction.PUBLISH);
        assertThat(service.history(id)).filteredOn(entry -> entry.action() == AuditAction.REQUEST_CHANGES)
                .singleElement().satisfies(entry -> assertThat(entry.details()).contains("instructions.fee").contains("Please update the fee"));
        assertThat(review.review(id).comments()).hasSize(2);
    }

    @Test void aReviewerCanResolveACorrectionExplicitlyAndOtherOrganizationsCannotSeeTheReview() throws Exception {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Visa", "Entry visa", 10L, null, null));
        service.submitForReview(card.getId());
        login(2L, REVIEWER);
        Long commentId = review.addComment(card.getId(), "section.other", "Add a source link if one exists").comments().get(0).id();
        perform(get("/api/functions/{id}/review", card.getId()).with(authentication(token(5L, List.of(20L), REVIEWER))))
                .andExpect(status().isForbidden());
        perform(post("/api/functions/{id}/review/comments/{comment}/resolve", card.getId(), commentId)
                .with(authentication(auth(2L, REVIEWER)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.comments[0].resolution").value("REVIEWER_RESOLVED"));
        perform(post("/api/functions/{id}/review/comments/{comment}/resolve", card.getId(), commentId)
                .with(authentication(auth(2L, REVIEWER)))).andExpect(status().isConflict());
        perform(get("/api/functions/{id}/review", card.getId()).with(authentication(auth(1L, AUTHOR)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(1));
        login(3L, PUBLISHER);
        assertThat(service.publish(card.getId()).getStatus()).isEqualTo(PUBLISHED);
    }

    @Test void revisionNotesMoveToThePublishedCardAndDiscardingKeepsThemAsHistory() {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Archive", "Archive copy", 10L, null, null));
        service.submitForReview(card.getId());
        publishAsReviewer(card.getId());
        login(1L, AUTHOR);
        OrgFunction revision = service.createRevision(card.getId());
        assertThat(revision.getReviewCycle()).isEqualTo(1);
        service.update(revision.getId(), new UpdateOrgFunctionRequest(null, "New text", null, null, null, null, null));
        service.submitForReview(revision.getId());
        login(2L, REVIEWER);
        review.addComment(revision.getId(), "description", "Too short");
        review.requestChanges(revision.getId(), null);
        login(1L, AUTHOR);
        service.discardRevision(card.getId());

        FunctionReviewResponse history = review.review(card.getId());
        assertThat(history.comments()).singleElement().satisfies(comment -> {
            assertThat(comment.functionId()).isEqualTo(card.getId());
            assertThat(comment.resolution()).isEqualTo(ReviewResolution.REVISION_DISCARDED);
        });
        // The cycle counter stays monotonic, so the next revision's notes never collide with the discarded ones.
        assertThat(functions.findById(card.getId()).orElseThrow().getReviewCycle()).isEqualTo(2);
        assertThat(service.createRevision(card.getId()).getReviewCycle()).isEqualTo(2);
    }

    private void publishAsReviewer(Long id) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        login(3L, PUBLISHER);
        if (functions.findById(id).orElseThrow().getStatus() != PENDING_REVIEW) {
            login(1L, List.of("FUNCTIONS_SUBMIT_REVIEW"));
            service.submitForReview(id);
            login(3L, PUBLISHER);
        }
        service.publish(id);
        SecurityContextHolder.getContext().setAuthentication(previous);
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

    private static UsernamePasswordAuthenticationToken auth(Long userId, List<String> permissions) {
        return token(userId, List.of(10L), permissions);
    }

    private static UsernamePasswordAuthenticationToken token(Long userId, List<Long> organizations, List<String> permissions) {
        var principal = new JwtPrincipal("staff" + userId + "@example.test", "ROLE_CUSTOM", organizations, permissions, userId);
        return new UsernamePasswordAuthenticationToken(principal, null, permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static void login(Long userId, List<String> permissions) {
        SecurityContextHolder.getContext().setAuthentication(auth(userId, permissions));
    }
}
