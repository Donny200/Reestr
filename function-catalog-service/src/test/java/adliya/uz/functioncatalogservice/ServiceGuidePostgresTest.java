package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.ServiceInstructions;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.InterfaceLabels;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public service pages and the downloadable PDF guide work without signing in and follow the requested language. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=guide-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false", "catalog.scheduling.enabled=false",
        "catalog.public-base-url=https://reestr.example"
})
@AutoConfigureMockMvc
class ServiceGuidePostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired OrgFunctionService functions;
    @Autowired OrgFunctionRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean InterfaceLabels labels;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE information_reports, audit_log_functions, audit_logs, function_public_changes, translation_jobs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(organizations.findPublic(10L)).thenReturn(Optional.of(new IdentityOrganizationClient.PublicOrganization(10L, "Justice office",
                Map.of("ru", new TranslatedText("Управление юстиции", TranslatedText.HUMAN)),
                new IdentityOrganizationClient.Contact("Tashkent, Sayilgoh 5", "+998 71 200-00-00", "Mon–Fri 9:00–18:00", null))));
        when(labels.forLanguage(anyString())).thenAnswer(call -> new InterfaceLabels.Labels(call.getArgument(0), Map.of()));
        when(labels.forLanguage("ru")).thenReturn(new InterfaceLabels.Labels("ru", Map.of(
                "instructions.steps", "Порядок действий", "instructions.requiredDocuments", "Необходимые документы",
                "checklist.title", "Ваш чек-лист подготовки", "guide.title", "Памятка по услуге")));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void publishedDetailsAndDocumentsArePublicWithoutAnAccount() throws Exception {
        OrgFunction card = published(new ServiceInstructions(null, "Fill in the form\nSubmit it", "Passport\nPhoto 3x4", null, "10 days", null));
        mvc.perform(get("/api/functions/" + card.getId()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions.requiredDocuments").value("Passport\nPhoto 3x4"))
                .andExpect(jsonPath("$.instructions.steps").value("Fill in the form\nSubmit it"))
                .andExpect(jsonPath("$.assignee").doesNotExist());
        mvc.perform(get("/api/functions").with(anonymous())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(card.getId()));
    }

    @Test void guideIsAnonymousPdfWithPublishedInformationAndChecklist() throws Exception {
        OrgFunction card = published(new ServiceInstructions("Adult citizens", "Fill in the form\n2. Submit it at the centre",
                "Passport\nPhoto 3x4", "At any public service centre", "10 days", "Free of charge"));
        byte[] pdf = mvc.perform(get("/api/functions/" + card.getId() + "/guide.pdf").param("lang", "en").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("service-guide-" + card.getId() + "-en.pdf")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = text(pdf);
        assertThat(text).contains("Passport registration", "Justice office", "Who can use this service", "Adult citizens",
                "Step-by-step procedure", "Submit it at the centre", "Required documents", "Photo 3x4", "Official fee", "Free of charge",
                "Processing time", "10 days", "Where and how to apply", "Tashkent, Sayilgoh 5", "+998 71 200-00-00",
                "Your preparation checklist", "https://reestr.example/functions/" + card.getId());
    }

    @Test void emptySectionsAreOmittedAndNoExternalSourceIsRequired() throws Exception {
        OrgFunction card = published(new ServiceInstructions(null, null, "Passport", null, null, null));
        assertThat(card.getOfficialSourceUrl()).isNull();
        String text = text(mvc.perform(get("/api/functions/" + card.getId() + "/guide.pdf").with(anonymous()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(text).contains("Required documents", "Passport");
        assertThat(text).doesNotContain("Official fee", "Processing time", "Step-by-step procedure", "Who can use this service",
                "Where and how to apply", "Not specified");
    }

    @Test void guideFollowsTheRequestedLanguageAndMarksFallbacks() throws Exception {
        OrgFunction card = published(new ServiceInstructions(null, "Fill in the form", "Passport", null, null, "Free of charge"));
        card.setNameTranslations(new LinkedHashMap<>(Map.of("ru", new TranslatedText("Регистрация паспорта", TranslatedText.HUMAN))));
        Map<String, Map<String, TranslatedText>> instructions = new LinkedHashMap<>();
        instructions.put("steps", Map.of("ru", new TranslatedText("Заполните анкету", TranslatedText.MACHINE)));
        instructions.put("requiredDocuments", Map.of("ru", new TranslatedText("Паспорт", TranslatedText.HUMAN)));
        card.setInstructionTranslations(instructions);
        repository.saveAndFlush(card);

        byte[] pdf = mvc.perform(get("/api/functions/" + card.getId() + "/guide.pdf").param("lang", "ru").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("-ru.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = text(pdf);
        assertThat(text).contains("Регистрация паспорта", "Управление юстиции", "Порядок действий", "Заполните анкету",
                "Необходимые документы", "Паспорт", "Ваш чек-лист подготовки", "Памятка по услуге",
                // the fee has no Russian translation: the original is shown with a note
                "Free of charge", "translated automatically", "original language");
        assertThat(text).doesNotContain("Fill in the form");
    }

    @Test void unpublishedCardsHaveNoGuide() throws Exception {
        login(superAdmin(with(EDITOR)));
        OrgFunction draft = functions.create(new CreateOrgFunctionRequest("Draft", "Description", 10L, null, null));
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/functions/" + draft.getId() + "/guide.pdf").with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(get("/api/functions/999999/guide.pdf").with(anonymous())).andExpect(status().isNotFound());
    }

    private OrgFunction published(ServiceInstructions instructions) {
        login(superAdmin(with(EDITOR)));
        var card = functions.create(new CreateOrgFunctionRequest("Passport registration", "Registering a passport", 10L, null, null,
                null, "en", instructions, null));
        functions.submitForReview(card.getId());
        OrgFunction result = functions.publish(card.getId());
        SecurityContextHolder.clearContext();
        return result;
    }

    private static String text(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString().replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
    }
}
