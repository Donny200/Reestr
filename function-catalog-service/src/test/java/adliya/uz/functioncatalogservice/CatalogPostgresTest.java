package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.*;
import adliya.uz.functioncatalogservice.entity.*;
import adliya.uz.functioncatalogservice.repository.*;
import adliya.uz.functioncatalogservice.security.JwtPrincipal;
import adliya.uz.functioncatalogservice.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.*;

@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=workflow-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false"
})
@AutoConfigureMockMvc
class CatalogPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired OrgFunctionService service;
    @Autowired FunctionAutoTranslationService automatic;
    @Autowired FunctionCategoryService categories;
    @Autowired FunctionImportService importer;
    @Autowired EditorialSeedService seeder;
    @Autowired OrgFunctionRepository functions;
    @Autowired FunctionCategoryRepository categoryRepository;
    @Autowired AuditLogRepository audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory entityManagers;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE translation_jobs, audit_log_functions, audit_logs, org_functions, function_categories RESTART IDENTITY CASCADE");
        login(List.of(10L), true);
        doAnswer(call -> {
            Long id = call.getArgument(0);
            if (id == null || (id != 10L && id != 20L)) throw new IllegalArgumentException("Unknown organization");
            return null;
        }).when(organizations).requireExisting(any());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void completeCyclePersistsAuditAndNeverPublishesOnReactivation() {
        var function = service.create(request("Услуга", 10L));
        assertThat(service.getAll()).isEmpty();
        service.submitForReview(function.getId());
        service.reject(function.getId(), "Уточнить документы");
        service.updateRequirements(function.getId(), "Документ для уточнения");
        service.submitForReview(function.getId());
        service.publish(function.getId());
        assertThat(service.getAll()).extracting(OrgFunction::getId).containsExactly(function.getId());
        service.deactivate(function.getId());
        assertThat(service.getAll()).isEmpty();
        service.reactivate(function.getId());
        assertThat(functions.findById(function.getId()).orElseThrow().getStatus()).isEqualTo(PENDING_REVIEW);
        assertThat(service.getAll()).isEmpty();
        var history = service.history(function.getId());
        assertThat(history).extracting(AuditLogResponse::action).containsExactly(AuditAction.CREATE, AuditAction.SUBMIT_REVIEW,
                AuditAction.REJECT, AuditAction.UPDATE, AuditAction.SUBMIT_REVIEW, AuditAction.PUBLISH, AuditAction.DEACTIVATE, AuditAction.REACTIVATE);
        assertThat(history).allSatisfy(event -> {
            assertThat(event.performedByUserId()).isEqualTo(42L); assertThat(event.performedBy()).isEqualTo("editor@example.com");
        });
        assertThat(history.get(2).details()).isEqualTo("Уточнить документы");
    }

    @Test void publicListDetailAndFiltersHideEveryUnpublishedStatus() throws Exception {
        var category = categories.create(new FunctionCategoryRequest("Registry", null));
        for (var status : FunctionStatus.values()) {
            functions.saveAndFlush(OrgFunction.builder().name(status.name()).organizationId(10L).functionCategory(category).status(status).build());
        }
        SecurityContextHolder.clearContext();
        for (String suffix : List.of("", "?organizationId=10", "?category=Registry")) {
            mvc.perform(get("/api/functions" + suffix)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("PUBLISHED"));
        }
        for (var function : functions.findAll()) {
            mvc.perform(get("/api/functions/" + function.getId()))
                    .andExpect(function.getStatus() == PUBLISHED ? status().isOk() : status().isNotFound());
        }
    }

    @Test void scopeAppliesToQueueAuditAndEveryWorkflowAction() {
        var foreign = functions.saveAndFlush(OrgFunction.builder().name("Foreign").organizationId(20L).status(PENDING_REVIEW).build());
        login(List.of(10L), false);
        assertThat(service.pendingReview()).isEmpty();
        assertThat(service.getAllForAdmin()).isEmpty();
        for (var call : List.<Runnable>of(() -> service.submitForReview(foreign.getId()), () -> service.reject(foreign.getId(),"Reason"),
                () -> service.publish(foreign.getId()), () -> service.deactivate(foreign.getId()),
                () -> service.reactivate(foreign.getId()), () -> service.history(foreign.getId()))) {
            assertThatThrownBy(call::run).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        assertThat(audit.count()).isZero();
    }

    @Test void categoryDeletionReturns409AndNeverDeletesFunctions() throws Exception {
        var category = categories.create(new FunctionCategoryRequest("Связанные", Map.of("en","Linked")));
        var function = service.create(new CreateOrgFunctionRequest("Draft", null, 10L, null, null, category.getId()));
        mvc.perform(delete("/api/functions/categories/" + category.getId()).with(authentication(auth(true))))
                .andExpect(status().isConflict());
        assertThat(functions.existsById(function.getId())).isTrue();
        assertThat(categoryRepository.existsById(category.getId())).isTrue();
    }

    @Test void translationEditorUsesExistingJsonAndBothPermissions() {
        var function = service.create(request("Исходное имя",10L));
        service.updateTranslations(function.getId(), new FunctionTranslationsRequest(Map.of("en","Human name","uz","Nom"), Map.of("en","Description")));
        var stored = functions.findById(function.getId()).orElseThrow();
        assertThat(stored.getNameTranslations()).containsEntry("en",new TranslatedText("Human name",TranslatedText.HUMAN));
        login(List.of(10L),true);
        assertThat(service.history(function.getId())).extracting(AuditLogResponse::action).contains(AuditAction.TRANSLATION_EDIT);
    }

    @Test void csvReportsPhysicalLinesPreservesUtf8AndCommitsOnlyValidRows() {
        login(List.of(10L), false);
        var result = importer.importFile(csv("\uFEFFname,description,organizationId\r\n\"Первая, услуга\",\"Две\nстроки\",10\r\n,Missing name,10\r\nForeign,Description,20\r\nПоследняя,Ўзбекча,10\r\n"));
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).extracting(FunctionImportResponse.RowError::line).containsExactly(4L,5L);
        assertThat(functions.findAll()).extracting(OrgFunction::getName).containsExactly("Первая, услуга","Последняя");
        assertThat(functions.findAll()).allMatch(f -> f.getStatus() == DRAFT);
        assertThat(audit.count()).isEqualTo(1);
        var history = service.history(result.functionIds().get(0));
        assertThat(history).hasSize(1);
        assertThat(history.get(0).action()).isEqualTo(AuditAction.IMPORT);
        assertThat(history.get(0).details()).isEqualTo("Imported this function as DRAFT");
        assertThat(audit.findById(result.auditId()).orElseThrow().getDetails()).contains("success=2", "errors=2");
    }

    @Test void databaseFailureRollsBackOnlyItsRowAndLeavesOneCompleteAuditEvent() {
        jdbc.execute("ALTER TABLE org_functions ADD CONSTRAINT test_reject_name CHECK (name <> 'Rejected at database')");
        try {
            var result = importer.importFile(csv("name,organizationId\nRejected at database,10\nValid after failure,10\n"));
            assertThat(result.imported()).isEqualTo(1); assertThat(result.failed()).isEqualTo(1);
            assertThat(functions.findAll()).extracting(OrgFunction::getName).containsExactly("Valid after failure");
            assertThat(audit.count()).isEqualTo(1);
            assertThat(service.history(result.functionIds().get(0))).hasSize(1);
        } finally { jdbc.execute("ALTER TABLE org_functions DROP CONSTRAINT test_reject_name"); }
    }

    @Test void unassignedDraftCannotBeSubmittedAndRemainsHidden() {
        var draft = service.create(request("Unassigned", null));
        assertThatThrownBy(() -> service.submitForReview(draft.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(functions.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(DRAFT);
        assertThat(service.getAll()).isEmpty();
        assertThat(service.history(draft.getId())).hasSize(1);
    }

    @Test void sharedImportAuditDoesNotRevealOtherOrganizations() {
        var result = importer.importFile(csv("name,organizationId\nOwn,10\nForeign,20\n"));
        assertThat(result.imported()).isEqualTo(2);
        login(List.of(10L), false);
        assertThat(service.history(result.functionIds().get(0)).get(0).details()).isEqualTo("Imported this function as DRAFT");
        assertThatThrownBy(() -> service.history(result.functionIds().get(1)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void malformedFileAndInvalidEncodingWriteNothing() {
        assertThatThrownBy(() -> importer.importFile(csv("name\n\"unclosed"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> importer.importFile(new MockMultipartFile("file","bad.csv","text/csv", new byte[]{(byte)0xff})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(functions.count()).isZero(); assertThat(audit.count()).isZero();
    }

    @Test void staleSessionCannotCreateUnauditedCard() {
        var principal = new JwtPrincipal("old@example.com","ROLE_SUPER_ADMIN",List.of(),List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        assertThatThrownBy(() -> service.create(request("Must roll back",10L)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(functions.count()).isZero(); assertThat(audit.count()).isZero();
    }

    @Test void seedIsIdempotentQueuesTranslationsOnceAndPreservesEditorialChanges() throws Exception {
        List<EditorialSeedService.Seed> seeds;
        try(var stream = new ClassPathResource("seed/editorial-functions.json").getInputStream()) {
            seeds = mapper.readValue(stream,new TypeReference<>() {});
        }
        seeds.forEach(seeder::seed);
        assertThat(functions.count()).isEqualTo(8);
        assertThat(functions.findAll()).allSatisfy(f -> {
            assertThat(f.getStatus()).isEqualTo(DRAFT); assertThat(f.getOrganizationId()).isNull();
            assertThat(f.getNameTranslations()).containsOnlyKeys("ru");
            assertThat(f.getNameTranslations().get("ru").source()).isEqualTo("human");
        });
        // Seeding never calls the provider; every draft is queued for the background worker instead.
        verifyNoInteractions(translator);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM translation_jobs WHERE target_type = 'FUNCTION' AND status = 'PENDING'", Long.class)).isEqualTo(8L);
        long auditCount = audit.count();
        var edited = functions.findBySeedKey(seeds.get(0).key()).orElseThrow();
        edited.setName("Редактор изменил название");
        functions.saveAndFlush(edited);
        var human = functions.findBySeedKey(seeds.get(1).key()).orElseThrow();
        human.getNameTranslations().put("en",new TranslatedText("Human translation",TranslatedText.HUMAN));
        functions.saveAndFlush(human);
        jdbc.update("UPDATE translation_jobs SET status = 'DONE'");
        seeds.forEach(seeder::seed);
        assertThat(functions.count()).isEqualTo(8);
        assertThat(audit.count()).isEqualTo(auditCount);
        assertThat(functions.findById(human.getId()).orElseThrow().getNameTranslations().get("en").text())
                .isEqualTo("Human translation");
        assertThat(functions.findById(edited.getId()).orElseThrow().getName()).isEqualTo("Редактор изменил название");
        // Restarting does not queue the same untouched drafts again.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM translation_jobs WHERE status = 'PENDING'", Long.class)).isZero();
    }

    @Test void migrationPreservesOldStatesCategoriesAndTranslationJson() {
        jdbc.execute("CREATE SCHEMA legacy_case");
        jdbc.execute("CREATE TABLE legacy_case.org_functions (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(150) NOT NULL, description VARCHAR(500), organization_id BIGINT NOT NULL, requirements VARCHAR(500), category VARCHAR(100), active BOOLEAN NOT NULL, name_translations JSONB, description_translations JSONB)");
        jdbc.execute("INSERT INTO legacy_case.org_functions (name,organization_id,category,active,name_translations) VALUES ('Old public',10,'Old category',true,'{\"ru\":{\"text\":\"Старое\",\"source\":\"human\"}}'),('Old disabled',10,'Old category',false,'{}')");
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .schemas("legacy_case").defaultSchema("legacy_case").baselineOnMigrate(true).baselineVersion("0").load().migrate();
        assertThat(jdbc.queryForList("SELECT status FROM legacy_case.org_functions ORDER BY id",String.class)).containsExactly("PUBLISHED","DEACTIVATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy_case.function_categories",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name_translations->'ru'->>'text' FROM legacy_case.org_functions WHERE id=1",String.class)).isEqualTo("Старое");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM legacy_case.org_functions WHERE category_id IS NOT NULL",Integer.class)).isEqualTo(2);
    }

    @Test void optimisticVersionPreventsOverwritingConcurrentDecision() {
        var function = service.create(request("Concurrent",10L));
        var first = entityManagers.createEntityManager(); var second = entityManagers.createEntityManager();
        try {
            first.getTransaction().begin(); second.getTransaction().begin();
            var a = first.find(OrgFunction.class,function.getId()); var b = second.find(OrgFunction.class,function.getId());
            a.setStatus(PENDING_REVIEW); first.getTransaction().commit();
            b.setName("Stale change");
            assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOf(jakarta.persistence.RollbackException.class);
        } finally {
            if(first.getTransaction().isActive()) first.getTransaction().rollback();
            if(second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close(); second.close();
        }
        assertThat(functions.findById(function.getId()).orElseThrow().getStatus()).isEqualTo(PENDING_REVIEW);
    }

    @Test void adminDetailReadsDraftButRejectsForeignOrganizationAndMissingAuthority() throws Exception {
        var own = service.create(request("Own",10L));
        var foreign = service.create(request("Foreign",20L));
        mvc.perform(get("/api/functions/{id}/admin",own.getId()).with(authentication(auth(false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/functions/{id}/admin",foreign.getId()).with(authentication(auth(false))))
                .andExpect(status().isForbidden());
        var viewer = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal("no-permission@example.test","ROLE_MODERATOR",List.of(10L),List.of(),5L),null,List.of());
        mvc.perform(get("/api/functions/{id}/admin",own.getId()).with(authentication(viewer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/functions/{id}",own.getId())).andExpect(status().isNotFound());
    }

    @Test void createApiPersistsOriginalLanguageAndRejectsMalformedLanguage() throws Exception {
        String body = mapper.writeValueAsString(new CreateOrgFunctionRequest("Maslahat","Tavsif",10L,"",null,null,"uz"));
        mvc.perform(post("/api/functions").with(authentication(auth(true))).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sourceLanguage").value("uz"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(functions.findAll().get(0).getSourceLanguage()).isEqualTo("uz");
        String invalid = mapper.writeValueAsString(new CreateOrgFunctionRequest("Invalid","Text",10L,"",null,null,"not a language"));
        mvc.perform(post("/api/functions").with(authentication(auth(true))).contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest());
        assertThat(functions.count()).isEqualTo(1);
    }

    @Test void oneLanguageEditPreservesOtherTextAndMachineSources() throws Exception {
        var function = service.create(request("Original",10L));
        function.setNameTranslations(new LinkedHashMap<>(Map.of("uz",new TranslatedText("Avtomatik","machine"))));
        function.setDescriptionTranslations(new LinkedHashMap<>(Map.of("uz",new TranslatedText("Tavsif","machine"))));
        functions.saveAndFlush(function);
        mvc.perform(put("/api/functions/{id}/translations/ru",function.getId()).with(authentication(auth(false)))
                        .contentType("application/json").content(mapper.writeValueAsString(new LanguageTranslationRequest("Перевод","Описание"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nameTranslations.ru.source").value("human"))
                .andExpect(jsonPath("$.nameTranslations.uz.source").value("machine"))
                .andExpect(jsonPath("$.name").value("Original"));
        login(List.of(10L),true);
        assertThat(service.history(function.getId())).extracting(AuditLogResponse::action).contains(AuditAction.TRANSLATION_EDIT);
        service.submitForReview(function.getId());
        mvc.perform(put("/api/functions/{id}/translations/ru",function.getId()).with(authentication(auth(true)))
                        .contentType("application/json").content(mapper.writeValueAsString(new LanguageTranslationRequest("Blocked",null))))
                .andExpect(status().isConflict());
    }

    @Test void manualTranslationRequiresBothPermissionsAndOrganizationScope() throws Exception {
        var function = service.create(request("Foreign",20L));
        String body = mapper.writeValueAsString(new LanguageTranslationRequest("No access",null));
        mvc.perform(put("/api/functions/{id}/translations/ru",function.getId()).with(authentication(auth(false)))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        var principal = new JwtPrincipal("editor@example.test","ROLE_SUPER_ADMIN",List.of(),List.of("FUNCTIONS_EDIT"),42L);
        var authentication = new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("FUNCTIONS_EDIT")));
        mvc.perform(put("/api/functions/{id}/translations/ru",function.getId()).with(authentication(authentication))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertThat(functions.findById(function.getId()).orElseThrow().getNameTranslations()).isEmpty();
    }

    @Test void autoTranslationDoesNotHoldTransactionDuringNetworkAndPreservesHumanEntries() {
        var function = service.create(request("Original",10L));
        service.updateLanguageTranslation(function.getId(),"ru",new LanguageTranslationRequest("Ручной","Ручное описание"));
        when(translator.isAvailable()).thenReturn(true);
        when(translator.translateRequired(any(),eq("en"),any())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat((List<String>)call.getArgument(2)).containsExactly("uz");
            return Map.of("uz","Avtomatik");
        });
        var saved = automatic.translate(function.getId(),new AutoTranslateRequest(List.of("ru","uz"),true));
        assertThat(saved.getNameTranslations().get("ru")).isEqualTo(new TranslatedText("Ручной","human"));
        assertThat(saved.getNameTranslations().get("uz").source()).isEqualTo("machine");
        assertThat(service.history(function.getId())).hasSize(3);
    }

    @Test void failedAutoTranslationIsAtomicAndReturnsSafeHttpError() throws Exception {
        var function = service.create(request("Original",10L));
        when(translator.isAvailable()).thenReturn(true);
        when(translator.translateRequired(eq("Original"),eq("en"),any())).thenReturn(Map.of("uz","Name"));
        when(translator.translateRequired(eq("Описание"),eq("en"),any())).thenThrow(
                new adliya.uz.functioncatalogservice.exception.TranslationUnavailableException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,"Translator did not respond"));
        mvc.perform(post("/api/functions/{id}/translate",function.getId()).with(authentication(auth(true)))
                        .contentType("application/json").content(mapper.writeValueAsString(new AutoTranslateRequest(List.of("uz"),false))))
                .andExpect(status().isGatewayTimeout());
        assertThat(functions.findById(function.getId()).orElseThrow().getNameTranslations()).isEmpty();
        login(List.of(10L),true);
        assertThat(service.history(function.getId())).hasSize(1);
    }

    @Test void automaticTranslationCannotOverwriteConcurrentWorkflowDecision() {
        var function = service.create(request("Original",10L));
        when(translator.isAvailable()).thenReturn(true);
        when(translator.translateRequired(eq("Original"),eq("en"),any())).thenAnswer(call -> {
            service.submitForReview(function.getId());
            return Map.of("uz","Name");
        });
        when(translator.translateRequired(eq("Описание"),eq("en"),any())).thenReturn(Map.of("uz","Description"));
        assertThatThrownBy(() -> automatic.translate(function.getId(),new AutoTranslateRequest(List.of("uz"),false)))
                .isInstanceOf(adliya.uz.functioncatalogservice.exception.WorkflowConflictException.class);
        var saved = functions.findById(function.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PENDING_REVIEW);
        assertThat(saved.getNameTranslations()).isEmpty();
    }

    @Test void missingAzureConfigurationLeavesDraftIntactAndReportsUnavailable() throws Exception {
        var function = service.create(request("Original",10L));
        mvc.perform(get("/api/functions/translation-capabilities").with(authentication(auth(true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/functions/{id}/translate",function.getId()).with(authentication(auth(true)))
                        .contentType("application/json").content(mapper.writeValueAsString(new AutoTranslateRequest(List.of("ru"),false))))
                .andExpect(status().isServiceUnavailable());
        login(List.of(10L),true);
        assertThat(service.history(function.getId())).hasSize(1);
    }

    @Test void originalLanguageEditsRefreshSeedMirrorsWithoutDiscardingOtherHumanTranslations() {
        var function = service.create(new CreateOrgFunctionRequest("Имя","Описание",10L,"",null,null,"ru"));
        function.setNameTranslations(new LinkedHashMap<>(Map.of("ru",new TranslatedText("Имя","human"),
                "en",new TranslatedText("Human","human"),"uz",new TranslatedText("Machine","machine"))));
        functions.saveAndFlush(function);
        var saved = service.update(function.getId(),new UpdateOrgFunctionRequest("Новое","Новое описание",10L,"",null,null,"ru"));
        assertThat(saved.getNameTranslations().get("ru").text()).isEqualTo("Новое");
        assertThat(saved.getNameTranslations().get("en").text()).isEqualTo("Human");
        assertThat(saved.getNameTranslations()).doesNotContainKey("uz");
    }

    private CreateOrgFunctionRequest request(String name, Long organization) {
        return new CreateOrgFunctionRequest(name,"Описание",organization,"Документы",null);
    }
    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file","drafts.csv","text/csv",content.getBytes(StandardCharsets.UTF_8));
    }
    private UsernamePasswordAuthenticationToken auth(boolean global) {
        var permissions = List.of("FUNCTIONS_CREATE","FUNCTIONS_EDIT","FUNCTIONS_MANAGE_REQUIREMENTS","FUNCTIONS_DEACTIVATE",
                "FUNCTIONS_SUBMIT_REVIEW","FUNCTIONS_REVIEW","FUNCTIONS_PUBLISH","FUNCTIONS_REACTIVATE","FUNCTIONS_TRANSLATIONS_EDIT",
                "FUNCTION_CATEGORIES_MANAGE","FUNCTIONS_IMPORT","AUDIT_VIEW","FUNCTIONS_VIEW");
        var principal = new JwtPrincipal("editor@example.com",global ? "ROLE_SUPER_ADMIN" : "ROLE_CUSTOM",List.of(10L),permissions,42L);
        return new UsernamePasswordAuthenticationToken(principal,null,permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }
    private void login(List<Long> orgIds, boolean global) { SecurityContextHolder.getContext().setAuthentication(auth(global)); }
}
