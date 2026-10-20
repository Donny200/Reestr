package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.config.MachineTranslationProperties;
import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.FunctionCategoryRequest;
import adliya.uz.functioncatalogservice.dto.ServiceInstructions;
import adliya.uz.functioncatalogservice.dto.TranslationBackfillResponse;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.entity.TranslationJob;
import adliya.uz.functioncatalogservice.entity.TranslationJobStatus;
import adliya.uz.functioncatalogservice.entity.TranslationTarget;
import adliya.uz.functioncatalogservice.exception.TranslationUnavailableException;
import adliya.uz.functioncatalogservice.repository.AuditLogRepository;
import adliya.uz.functioncatalogservice.repository.FunctionCategoryRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.TranslationJobRepository;
import adliya.uz.functioncatalogservice.service.ActiveLanguageClient;
import adliya.uz.functioncatalogservice.service.EditorialSeedService;
import adliya.uz.functioncatalogservice.service.FunctionCategoryService;
import adliya.uz.functioncatalogservice.service.FunctionImportService;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.MachineTranslationQueue;
import adliya.uz.functioncatalogservice.service.MachineTranslationWorker;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import adliya.uz.functioncatalogservice.support.TestPrincipals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Background machine translation: queueing on every write path, filling only gaps, retries, and the staff view. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=workflow-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false",
        "catalog.translation.batch-size=2", "catalog.translation.max-attempts=2", "catalog.translation.interval=PT20S"
})
@AutoConfigureMockMvc
class MachineTranslationPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Jobs are queued with the real clock, so "now" for the worker must be at or after that moment. */
    private final Instant NOW = Instant.now().plusSeconds(1);

    @Autowired OrgFunctionService service;
    @Autowired FunctionCategoryService categoryService;
    @Autowired FunctionImportService importer;
    @Autowired EditorialSeedService seeder;
    @Autowired MachineTranslationQueue queue;
    @Autowired MachineTranslationWorker worker;
    @Autowired MachineTranslationProperties properties;
    @Autowired OrgFunctionRepository functions;
    @Autowired FunctionCategoryRepository categories;
    @Autowired TranslationJobRepository jobs;
    @Autowired AuditLogRepository audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean ActiveLanguageClient languages;
    @MockitoBean TranslationClient translator;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE translation_jobs, audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        TestPrincipals.login(TestPrincipals.superAdmin(TestPrincipals.with(TestPrincipals.EDITOR, "FUNCTIONS_IMPORT")));
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(languages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "ru", "uz")));
        when(translator.isAvailable()).thenReturn(true);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void createQueuesAndWorkerFillsEveryActiveLanguageExceptSourceWithoutTouchingHumanText() {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Legal aid", "Free advice", 10L, null, null, null, "en",
                new ServiceInstructions("Residents", "Apply\nWait", null, null, "5 days", null), null));
        TranslationJob queued = jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, card.getId()).orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(TranslationJobStatus.PENDING);
        assertThat(queued.getAttempts()).isZero();

        // A human already translated the name into Russian: that text must survive and must not be requested again.
        card.getNameTranslations().put("ru", new TranslatedText("Юридическая помощь", TranslatedText.HUMAN));
        functions.saveAndFlush(card);

        when(translator.translateBatch(anyList(), eq("en"), anyList())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            List<String> to = call.getArgument(2);
            return texts.stream().map(text -> to.stream().collect(java.util.stream.Collectors.toMap(code -> code, code -> code + ":" + text))).toList();
        });
        MachineTranslationWorker.RunResult result = worker.runDue(NOW);
        assertThat(result.claimed()).isEqualTo(1);
        assertThat(result.done()).isEqualTo(1);

        OrgFunction translated = functions.findById(card.getId()).orElseThrow();
        assertThat(translated.getNameTranslations().get("ru").text()).isEqualTo("Юридическая помощь");
        assertThat(translated.getNameTranslations().get("ru").source()).isEqualTo(TranslatedText.HUMAN);
        assertThat(translated.getNameTranslations().get("uz")).isEqualTo(new TranslatedText("uz:Legal aid", TranslatedText.MACHINE));
        assertThat(translated.getNameTranslations()).doesNotContainKey("en");
        assertThat(translated.getDescriptionTranslations().get("ru").text()).isEqualTo("ru:Free advice");
        assertThat(translated.getInstructionTranslations().get("steps").get("uz").text()).isEqualTo("uz:Apply\nWait");
        assertThat(translated.getInstructionTranslations().get("processingTime").get("ru").text()).isEqualTo("ru:5 days");
        assertThat(translated.getInstructionTranslations()).doesNotContainKey("fee");
        // Texts missing the same languages travel in one request; the name (only uz missing) in another.
        verify(translator, times(2)).translateBatch(anyList(), eq("en"), anyList());
        verify(translator).translateBatch(eq(List.of("Legal aid")), eq("en"), eq(List.of("uz")));

        TranslationJob done = jobs.findById(queued.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(TranslationJobStatus.DONE);
        assertThat(done.getAttempts()).isEqualTo(1);
        assertThat(done.getLastError()).isNull();
        assertThat(audit.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.TRANSLATION_EDIT);
            assertThat(log.getPerformedBy()).isEqualTo("system:machine-translation");
            assertThat(log.getDetails()).isEqualTo("Automatic translation: ru, uz");
        });

        // Second run: nothing is missing, so the provider is not called again.
        queue.enqueue(TranslationTarget.FUNCTION, card.getId());
        worker.runDue(NOW.plusSeconds(60));
        verify(translator, times(2)).translateBatch(anyList(), eq("en"), anyList());
        assertThat(jobs.findById(queued.getId()).orElseThrow().getStatus()).isEqualTo(TranslationJobStatus.DONE);
    }

    @Test void failuresBackOffThenGiveUpAndStaffCanRequeue() throws Exception {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Name", "Description", 10L, null, null));
        when(translator.translateBatch(anyList(), any(), anyList()))
                .thenThrow(new TranslationUnavailableException(HttpStatus.TOO_MANY_REQUESTS, "Translator rate limit reached; retry later"));

        assertThat(worker.runDue(NOW).retried()).isEqualTo(1);
        TranslationJob job = jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, card.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(TranslationJobStatus.PENDING);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(job.getLastError()).contains("rate limit");
        assertThat(functions.findById(card.getId()).orElseThrow().getNameTranslations()).doesNotContainKey("ru");

        assertThat(worker.runDue(NOW.plusSeconds(30)).claimed()).isZero();
        worker.runDue(NOW.plus(Duration.ofMinutes(6)));
        job = jobs.findById(job.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(TranslationJobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getLastError()).startsWith("Gave up after 2 attempts");

        mvc.perform(post("/api/functions/" + card.getId() + "/translation-job")
                        .with(authentication(TestPrincipals.superAdmin("FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.missingLanguages").isArray());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(TranslationJobStatus.PENDING);
    }

    @Test void publishedCardsAreSkippedAndResumedWhenReturnedToDraft() {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Name", "Description", 10L, null, null));
        service.submitForReview(card.getId());
        assertThat(worker.runDue(NOW).skipped()).isEqualTo(1);
        TranslationJob job = jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, card.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(TranslationJobStatus.SKIPPED);
        assertThat(job.getLastError()).contains("PENDING_REVIEW");
        verify(translator, never()).translateBatch(anyList(), any(), anyList());

        service.reject(card.getId(), "Needs work");
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(TranslationJobStatus.PENDING);
    }

    @Test void contentChangeDuringTranslationRequeuesInsteadOfStoringStaleText() {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Old name", "Description", 10L, null, null));
        when(translator.translateBatch(anyList(), eq("en"), anyList())).thenAnswer(call -> {
            service.update(card.getId(), new UpdateOrgFunctionRequest("New name", null, null, null, null, null, null, null, null));
            List<String> texts = call.getArgument(0);
            return texts.stream().map(text -> Map.of("ru", "stale", "uz", "stale")).toList();
        });
        assertThat(worker.runDue(NOW).retried()).isEqualTo(1);
        OrgFunction current = functions.findById(card.getId()).orElseThrow();
        assertThat(current.getName()).isEqualTo("New name");
        assertThat(current.getNameTranslations()).doesNotContainKeys("ru", "uz");
        TranslationJob job = jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, card.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(TranslationJobStatus.PENDING);
        assertThat(job.getLastError()).contains("queued again");
    }

    @Test void importSeedAndCategoryPathsQueueWithoutCallingTheProvider() throws Exception {
        importer.importFile(new MockMultipartFile("file", "f.csv", "text/csv",
                "name,description,organizationId\nImported,Text,10\n".getBytes(StandardCharsets.UTF_8)));
        seeder.seed(new EditorialSeedService.Seed("seed-1", "Название", "Описание", "Документы", "Категория"));
        seeder.seed(new EditorialSeedService.Seed("seed-1", "Название", "Описание", "Документы", "Категория"));
        FunctionCategory category = categoryService.create(new FunctionCategoryRequest("Housing", Map.of("ru", "Жильё"), "en"));

        assertThat(jobs.countByStatus(TranslationJobStatus.PENDING)).isEqualTo(4); // import, seed, seed category, category
        verify(translator, never()).translateBatch(anyList(), any(), anyList());
        assertThat(categories.findById(category.getId()).orElseThrow().getSourceLanguage()).isEqualTo("en");

        when(translator.translateBatch(anyList(), any(), anyList())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            List<String> to = call.getArgument(2);
            return texts.stream().map(text -> to.stream().collect(java.util.stream.Collectors.toMap(code -> code, code -> "[" + code + "] " + text))).toList();
        });
        worker.runDue(NOW);
        worker.runDue(NOW.plusSeconds(1));
        FunctionCategory housing = categories.findById(category.getId()).orElseThrow();
        assertThat(housing.getNameTranslations().get("ru").text()).isEqualTo("Жильё"); // human, preserved
        assertThat(housing.getNameTranslations().get("uz").text()).isEqualTo("[uz] Housing");
        assertThat(housing.getNameTranslations()).doesNotContainKey("en");
        OrgFunction seeded = functions.findBySeedKey("seed-1").orElseThrow();
        assertThat(seeded.getNameTranslations().get("en").text()).isEqualTo("[en] Название");
        assertThat(seeded.getNameTranslations().get("ru").source()).isEqualTo(TranslatedText.HUMAN);
        assertThat(jobs.countByStatus(TranslationJobStatus.DONE)).isEqualTo(4);
    }

    @Test void backfillIsBoundedIdempotentAndOrganizationScoped() {
        OrgFunction mine = service.create(new CreateOrgFunctionRequest("A", "D", 10L, null, null));
        OrgFunction other = service.create(new CreateOrgFunctionRequest("B", "D", 20L, null, null));
        OrgFunction complete = service.create(new CreateOrgFunctionRequest("C", null, 10L, null, null));
        complete.getNameTranslations().put("ru", new TranslatedText("В", TranslatedText.HUMAN));
        complete.getNameTranslations().put("uz", new TranslatedText("S", TranslatedText.HUMAN));
        functions.saveAndFlush(complete);
        jdbc.update("UPDATE translation_jobs SET status = 'DONE'");

        TestPrincipals.login(TestPrincipals.staff(7L, List.of(10L), "FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"));
        TranslationBackfillResponse first = queue.backfill(null, 10);
        assertThat(first.queued()).isEqualTo(1);
        assertThat(first.examined()).isEqualTo(2);
        assertThat(jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, mine.getId()).orElseThrow().getStatus()).isEqualTo(TranslationJobStatus.PENDING);
        assertThat(jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, other.getId()).orElseThrow().getStatus()).isEqualTo(TranslationJobStatus.DONE);
        assertThat(jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, complete.getId()).orElseThrow().getStatus()).isEqualTo(TranslationJobStatus.DONE);
        TranslationBackfillResponse second = queue.backfill(null, 10);
        assertThat(second.queued()).isZero();
        assertThat(second.alreadyQueued()).isEqualTo(1);
        assertThatThrownBy(() -> queue.backfill(20L, 10)).isInstanceOf(AccessDeniedException.class);

        TestPrincipals.login(TestPrincipals.superAdmin("FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"));
        TranslationBackfillResponse global = queue.backfill(null, 1);
        assertThat(global.queued()).isEqualTo(1);
        assertThat(global.limitReached()).isTrue();
        assertThat(jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, other.getId()).orElseThrow().getStatus()).isEqualTo(TranslationJobStatus.PENDING);
    }

    @Test void staffSeeTranslationStateOnTheirCardsAndOtherOrganizationsCannotRequeue() throws Exception {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Name", "Description", 10L, null, null));
        mvc.perform(get("/api/functions/" + card.getId() + "/admin")
                        .with(authentication(TestPrincipals.staff(7L, List.of(10L), "FUNCTIONS_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineTranslation.status").value("PENDING"))
                .andExpect(jsonPath("$.machineTranslation.automatic").value(true))
                .andExpect(jsonPath("$.machineTranslation.missingLanguages").value(org.hamcrest.Matchers.contains("ru", "uz")));
        mvc.perform(get("/api/functions/" + card.getId()))
                .andExpect(status().isNotFound()); // draft: not public, and no translation state leaks on public routes
        mvc.perform(post("/api/functions/" + card.getId() + "/translation-job")
                        .with(authentication(TestPrincipals.staff(8L, List.of(20L), "FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/functions/translations/backfill").param("organizationId", "20")
                        .with(authentication(TestPrincipals.staff(8L, List.of(10L), "FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/functions/translations/backfill").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isForbidden()); // rejected before any service code runs
        mvc.perform(get("/api/functions/translation-capabilities")
                        .with(authentication(TestPrincipals.superAdmin("FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.automatic").value(true));
    }

    @Test void staffListCanBePagedServerSideWhileTheDefaultContractStays() throws Exception {
        for (int i = 0; i < 3; i++) {
            service.create(new CreateOrgFunctionRequest("Card " + i, "D", 10L, null, null));
        }
        service.create(new CreateOrgFunctionRequest("Other org", "D", 20L, null, null));
        var staff = TestPrincipals.staff(7L, List.of(10L), "FUNCTIONS_VIEW");
        mvc.perform(get("/api/functions/admin").with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("X-Total-Count"));
        mvc.perform(get("/api/functions/admin").param("page", "1").param("size", "2").with(authentication(staff)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Card 2"))
                .andExpect(jsonPath("$[0].machineTranslation.status").value("PENDING"));
        mvc.perform(get("/api/functions/admin").param("page", "0").param("size", "500")
                        .with(authentication(TestPrincipals.superAdmin("FUNCTIONS_VIEW"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Total-Count", "4"))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test void workerWaitsWhenActiveLanguagesAreUnknownAndMarksFunctionStatus() {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Name", "Description", 10L, null, null));
        when(languages.activeLanguageCodes()).thenReturn(Optional.empty());
        assertThat(worker.runDue(NOW).retried()).isEqualTo(1);
        TranslationJob job = jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, card.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(TranslationJobStatus.PENDING);
        assertThat(job.getLastError()).contains("Active languages are unavailable");
        verify(translator, never()).translateBatch(anyList(), any(), anyList());
        assertThat(functions.findById(card.getId()).orElseThrow().getStatus()).isEqualTo(FunctionStatus.DRAFT);
    }

    /** The mocked provider cannot write Cyrillic Uzbek, like Azure; every other language works. */
    private void cyrillicUzbekUnsupported() {
        when(translator.unsupportedTargets(any(), anyCollection())).thenAnswer(call -> {
            java.util.Collection<String> to = call.getArgument(1);
            return to.stream().filter("uz-cyrl"::equals).collect(java.util.stream.Collectors.toSet());
        });
        when(translator.translateBatch(anyList(), any(), anyList())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            List<String> to = call.getArgument(2);
            return texts.stream().map(text -> to.stream().collect(java.util.stream.Collectors.toMap(code -> code, code -> code + ":" + text))).toList();
        });
    }

    @Test void unsupportedActiveLanguagesAreReportedAndNeverBlockSupportedOnes() throws Exception {
        when(languages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "ru", "uz-cyrl")));
        cyrillicUzbekUnsupported();
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Legal aid", "Free advice", 10L, null, null));
        card.getDescriptionTranslations().put("ru", new TranslatedText("Бесплатно", TranslatedText.HUMAN));
        functions.saveAndFlush(card);

        assertThat(worker.runDue(NOW).done()).isEqualTo(1);
        verify(translator).translateBatch(eq(List.of("Legal aid")), eq("en"), eq(List.of("ru")));
        verify(translator, never()).translateBatch(anyList(), any(), org.mockito.ArgumentMatchers.argThat(to -> to != null && to.contains("uz-cyrl")));
        OrgFunction translated = functions.findById(card.getId()).orElseThrow();
        assertThat(translated.getNameTranslations().get("ru").text()).isEqualTo("ru:Legal aid");
        assertThat(translated.getDescriptionTranslations().get("ru")).isEqualTo(new TranslatedText("Бесплатно", TranslatedText.HUMAN));
        assertThat(translated.getNameTranslations()).doesNotContainKey("uz-cyrl");
        TranslationJob job = jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, card.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(TranslationJobStatus.DONE);
        assertThat(job.getLastError()).contains("Not supported by Azure Translator: uz-cyrl");

        mvc.perform(get("/api/functions/" + card.getId() + "/admin")
                        .with(authentication(TestPrincipals.staff(7L, List.of(10L), "FUNCTIONS_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineTranslation.missingLanguages").value(org.hamcrest.Matchers.contains("uz-cyrl")))
                .andExpect(jsonPath("$.machineTranslation.unsupportedLanguages").value(org.hamcrest.Matchers.contains("uz-cyrl")));

        // Only unsupported gaps remain: a backfill does not queue the card again just to skip it.
        TestPrincipals.login(TestPrincipals.superAdmin("FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"));
        TranslationBackfillResponse backfill = queue.backfill(null, 10);
        assertThat(backfill.queued()).isZero();
    }

    @Test void immediateTranslationSkipsUnsupportedLanguagesInsteadOfFailing() throws Exception {
        cyrillicUzbekUnsupported();
        when(translator.translateRequired(anyString(), eq("en"), anyList())).thenAnswer(call -> {
            List<String> to = call.getArgument(2);
            String text = call.getArgument(0);
            return to.stream().collect(java.util.stream.Collectors.toMap(code -> code, code -> code + ":" + text));
        });
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Legal aid", null, 10L, null, null));
        mvc.perform(post("/api/functions/" + card.getId() + "/translate")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"languages\":[\"ru\",\"uz-cyrl\"],\"overwriteMachine\":false}")
                        .with(authentication(TestPrincipals.superAdmin("FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameTranslations.ru.text").value("ru:Legal aid"))
                .andExpect(jsonPath("$.nameTranslations['uz-cyrl']").doesNotExist());
        verify(translator).translateRequired(eq("Legal aid"), eq("en"), eq(List.of("ru")));
    }

    @Test void unsupportedSourceLanguageFinishesWithANoteAndNoProviderCall() {
        when(translator.sourceUnsupported("uz-cyrl")).thenReturn(true);
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Юридик ёрдам", null, 10L, null, null, null, "uz-cyrl", null, null));
        assertThat(worker.runDue(NOW).done()).isEqualTo(1);
        verify(translator, never()).translateBatch(anyList(), any(), anyList());
        TranslationJob job = jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, card.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(TranslationJobStatus.DONE);
        assertThat(job.getLastError()).contains("Source language uz-cyrl is not supported");
    }

    @Test void publishedCardsAreBackfilledThroughDraftRevisionsWithoutChangingThePublicRow() throws Exception {
        OrgFunction card = service.create(new CreateOrgFunctionRequest("Passport", "Public text", 10L, "Docs", null));
        card.getNameTranslations().put("ru", new TranslatedText("Паспорт", TranslatedText.HUMAN));
        functions.saveAndFlush(card);
        service.submitForReview(card.getId());
        OrgFunction published = service.publish(card.getId());
        OrgFunction foreign = service.create(new CreateOrgFunctionRequest("Foreign", "Text", 20L, "Docs", null));
        service.submitForReview(foreign.getId());
        service.publish(foreign.getId());
        OrgFunction underReview = service.create(new CreateOrgFunctionRequest("Licence", "Text", 10L, "Docs", null));
        service.submitForReview(underReview.getId());
        service.publish(underReview.getId());
        OrgFunction pendingRevision = service.createRevision(underReview.getId());
        service.submitForReview(pendingRevision.getId());
        jdbc.update("UPDATE translation_jobs SET status = 'DONE'");
        Long publishedVersion = functions.findById(published.getId()).orElseThrow().getVersion();
        long auditBefore = audit.count();

        TestPrincipals.login(TestPrincipals.staff(7L, List.of(10L), "FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"));
        assertThat(queue.backfill(null, 10).revisionsCreated()).isZero(); // default: published cards untouched
        assertThat(functions.findByRevisionOfId(published.getId())).isEmpty();

        TranslationBackfillResponse first = queue.backfill(null, 10, true);
        assertThat(first.revisionsCreated()).isEqualTo(1);
        assertThat(first.skippedUnderReview()).isEqualTo(1);
        assertThat(first.queued()).isEqualTo(1);
        OrgFunction revision = functions.findByRevisionOfId(published.getId()).orElseThrow();
        assertThat(revision.getStatus()).isEqualTo(FunctionStatus.DRAFT);
        assertThat(functions.findByRevisionOfId(foreign.getId())).isEmpty(); // other organization: out of scope
        assertThat(jobs.findByTargetTypeAndTargetId(TranslationTarget.FUNCTION, revision.getId()).orElseThrow().getStatus())
                .isEqualTo(TranslationJobStatus.PENDING);
        assertThat(audit.count()).isEqualTo(auditBefore + 1);
        assertThat(audit.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.REVISION_CREATE);
            assertThat(log.getPerformedByUserId()).isEqualTo(7L);
        });

        TranslationBackfillResponse second = queue.backfill(null, 10, true);
        assertThat(second.revisionsCreated()).isZero();
        assertThat(second.queued()).isZero();
        assertThat(second.alreadyQueued()).isEqualTo(1);

        when(translator.translateBatch(anyList(), eq("en"), anyList())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            List<String> to = call.getArgument(2);
            return texts.stream().map(text -> to.stream().collect(java.util.stream.Collectors.toMap(code -> code, code -> code + ":" + text))).toList();
        });
        assertThat(worker.runDue(NOW).done()).isEqualTo(1);
        OrgFunction translatedRevision = functions.findById(revision.getId()).orElseThrow();
        assertThat(translatedRevision.getNameTranslations().get("ru")).isEqualTo(new TranslatedText("Паспорт", TranslatedText.HUMAN));
        assertThat(translatedRevision.getNameTranslations().get("uz").text()).isEqualTo("uz:Passport");
        assertThat(translatedRevision.getDescriptionTranslations().get("ru").text()).isEqualTo("ru:Public text");

        OrgFunction publicRow = functions.findById(published.getId()).orElseThrow();
        assertThat(publicRow.getStatus()).isEqualTo(FunctionStatus.PUBLISHED);
        assertThat(publicRow.getVersion()).isEqualTo(publishedVersion);
        assertThat(publicRow.getNameTranslations()).containsOnlyKeys("ru");
        assertThat(publicRow.getDescriptionTranslations()).isEmpty();

        // Complete revision: nothing to queue on a third call.
        TranslationBackfillResponse third = queue.backfill(null, 10, true);
        assertThat(third.queued()).isZero();
        assertThat(third.revisionsCreated()).isZero();

        mvc.perform(post("/api/functions/translations/backfill").param("includePublished", "true")
                        .with(authentication(TestPrincipals.staff(7L, List.of(10L), "FUNCTIONS_EDIT", "FUNCTIONS_TRANSLATIONS_EDIT"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.revisionsCreated").value(0))
                .andExpect(jsonPath("$.revisionsQueued").value(0))
                .andExpect(jsonPath("$.skippedUnderReview").value(1));
    }
}
