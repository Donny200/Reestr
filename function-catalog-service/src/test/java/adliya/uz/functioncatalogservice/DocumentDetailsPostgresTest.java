package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.ServiceInstructions;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.service.FunctionVerificationService;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Structured required-document details: backward compatible, validated against the document list, reviewed like other content. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=documents-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false", "catalog.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class DocumentDetailsPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String FORM = "https://gov.example/forms/application.pdf";

    @Autowired OrgFunctionService service;
    @Autowired FunctionVerificationService verification;
    @Autowired OrgFunctionRepository functions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE audit_log_functions, audit_logs, function_public_changes, translation_jobs, org_functions, function_categories "
                + "RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        login(superAdmin(with(EDITOR)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void existingCardsWithoutDetailsStayValidAndShowAnEmptyList() throws Exception {
        OrgFunction legacy = publish(service.create(new CreateOrgFunctionRequest("Legacy", "Old card", 10L, "Passport, photo", null)));
        jdbc.update("UPDATE org_functions SET document_details = NULL WHERE id = ?", legacy.getId());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/functions/{id}", legacy.getId()).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDetails.length()").value(0))
                .andExpect(jsonPath("$.requirements").value("Passport, photo"));
    }

    @Test void detailsAreStoredPerDocumentLineAndEmptyOnesAreNotKept() {
        OrgFunction card = service.create(create(List.of(
                new DocumentDetail(" Passport ", DocumentDetail.Form.ORIGINAL_AND_COPY, 2, " Valid for at least 6 months ", "", FORM),
                new DocumentDetail("Photo 3x4", null, null, " ", null, null))));
        OrgFunction stored = functions.findById(card.getId()).orElseThrow();
        assertThat(stored.getDocumentDetails()).containsExactly(
                new DocumentDetail("Passport", DocumentDetail.Form.ORIGINAL_AND_COPY, 2, "Valid for at least 6 months", null, FORM));
    }

    @Test void detailsMustBelongToTheDocumentListAndLinksMustBeWebLinks() throws Exception {
        assertThatThrownBy(() -> service.create(create(List.of(new DocumentDetail("Birth certificate", DocumentDetail.Form.COPY, null, null, null, null)))))
                .isInstanceOf(InvalidFieldException.class).hasMessageContaining("line of Required documents");
        assertThatThrownBy(() -> service.create(create(List.of(new DocumentDetail("Passport", DocumentDetail.Form.COPY, null, null, null, null),
                new DocumentDetail("Passport", DocumentDetail.Form.ORIGINAL, null, null, null, null)))))
                .isInstanceOf(InvalidFieldException.class);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Card");
        body.put("organizationId", 10);
        body.put("instructions", Map.of("requiredDocuments", "Passport\nPhoto 3x4"));
        body.put("documentDetails", List.of(Map.of("document", "Passport", "formUrl", "javascript:alert(1)")));
        mvc.perform(post("/api/functions").with(authentication(superAdmin(with(EDITOR)))).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['documentDetails[0].formUrl']").exists());
        body.put("documentDetails", List.of(Map.of("document", "Passport", "copies", 0)));
        mvc.perform(post("/api/functions").with(authentication(superAdmin(with(EDITOR)))).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
        body.put("documentDetails", List.of(Map.of("document", "Passport", "form", "STAMPED")));
        mvc.perform(post("/api/functions").with(authentication(superAdmin(with(EDITOR)))).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andExpect(status().is4xxClientError());
        assertThat(functions.count()).isZero();
    }

    @Test void publishedDetailsChangeOnlyThroughAReviewedRevision() throws Exception {
        OrgFunction published = publish(service.create(create(List.of())));
        assertThatThrownBy(() -> service.update(published.getId(), update(List.of(passportOriginal()))))
                .isInstanceOf(WorkflowConflictException.class);

        OrgFunction revision = service.createRevision(published.getId());
        service.update(revision.getId(), update(List.of(passportOriginal())));
        SecurityContextHolder.clearContext();
        // Visitors keep seeing the reviewed version until the revision is published.
        mvc.perform(get("/api/functions/{id}", published.getId()).with(anonymous()))
                .andExpect(jsonPath("$.documentDetails.length()").value(0));

        login(superAdmin(with(EDITOR)));
        service.submitForReview(revision.getId());
        service.publish(revision.getId());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/functions/{id}", published.getId()).with(anonymous()))
                .andExpect(jsonPath("$.documentDetails.length()").value(1))
                .andExpect(jsonPath("$.documentDetails[0].document").value("Passport"))
                .andExpect(jsonPath("$.documentDetails[0].form").value("ORIGINAL"))
                .andExpect(jsonPath("$.documentDetails[0].copies").value(1))
                .andExpect(jsonPath("$.documentDetails[0].formUrl").value(FORM));
        // The public history names the documents section as changed.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM function_public_changes WHERE function_id = ? AND change_type = 'UPDATE'",
                Long.class, published.getId())).isEqualTo(1);
    }

    @Test void removingADocumentLineDropsItsDetailsAndDetailChangesOutdateVerification() {
        OrgFunction card = service.create(create(List.of(passportOriginal())));
        verification.verify(card.getId());
        service.update(card.getId(), update(List.of(new DocumentDetail("Passport", DocumentDetail.Form.COPY, 3, null, null, null))));
        assertThat(functions.findById(card.getId()).orElseThrow().isVerificationOutdated()).isTrue();

        service.update(card.getId(), new UpdateOrgFunctionRequest(null, null, null, null, null, null, null,
                new ServiceInstructions(null, null, "Photo 3x4", null, null, null), null));
        assertThat(functions.findById(card.getId()).orElseThrow().getDocumentDetails()).isNull();
    }

    // ---------------------------------------------------------------- helpers

    private static DocumentDetail passportOriginal() {
        return new DocumentDetail("Passport", DocumentDetail.Form.ORIGINAL, 1, null, "Bring the original for inspection", FORM);
    }

    private static CreateOrgFunctionRequest create(List<DocumentDetail> details) {
        return new CreateOrgFunctionRequest("Passport service", "Issue a passport", 10L, null, null, null, "en",
                new ServiceInstructions(null, "Apply\nCollect", "Passport\nPhoto 3x4", null, null, null), null, details);
    }

    private static UpdateOrgFunctionRequest update(List<DocumentDetail> details) {
        return new UpdateOrgFunctionRequest(null, null, null, null, null, null, null, null, null, details);
    }

    private OrgFunction publish(OrgFunction card) {
        service.submitForReview(card.getId());
        return service.publish(card.getId());
    }
}
