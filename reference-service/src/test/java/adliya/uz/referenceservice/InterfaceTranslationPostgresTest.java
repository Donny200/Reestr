package adliya.uz.referenceservice;

import adliya.uz.referenceservice.catalog.InterfaceCatalog;
import adliya.uz.referenceservice.catalog.InterfaceCatalogSynchronizer;
import adliya.uz.referenceservice.dto.InterfaceTranslationExport;
import adliya.uz.referenceservice.entity.InterfaceTranslationJob;
import adliya.uz.referenceservice.entity.InterfaceTranslationJobStatus;
import adliya.uz.referenceservice.repository.InterfaceTranslationJobRepository;
import adliya.uz.referenceservice.security.SimpleJwtService;
import adliya.uz.referenceservice.service.InterfaceCoverageService;
import adliya.uz.referenceservice.service.InterfaceTranslationService;
import adliya.uz.referenceservice.service.InterfaceTranslationTransferService;
import adliya.uz.referenceservice.translation.ActiveLanguagesClient;
import adliya.uz.referenceservice.translation.AzureInterfaceTranslator;
import adliya.uz.referenceservice.translation.InterfaceMachineTranslationService;
import adliya.uz.referenceservice.translation.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Interface dictionary end to end on PostgreSQL 16: Flyway migrations, the start-up catalog synchronization, coverage,
 * and the persistent machine translation queue with a mocked provider.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=reference-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "interface.scheduling.enabled=false",
        "azure.translator.endpoint=", "azure.translator.key=test-secret-key-value", "azure.translator.region=westeurope",
        "interface.translation.texts-per-request=400", "interface.translation.max-attempts=2"
})
@AutoConfigureMockMvc
class InterfaceTranslationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired InterfaceCatalog catalog;
    @Autowired InterfaceCatalogSynchronizer synchronizer;
    @Autowired InterfaceTranslationService translations;
    @Autowired InterfaceCoverageService coverage;
    @Autowired InterfaceMachineTranslationService machine;
    @Autowired InterfaceTranslationJobRepository jobs;
    @Autowired InterfaceTranslationTransferService transfer;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean ActiveLanguagesClient activeLanguages;
    @MockitoBean AzureInterfaceTranslator translator;
    @MockitoBean SimpleJwtService jwtService;

    private final Instant now = Instant.now().plusSeconds(1);

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM interface_translation_jobs");
        jdbc.update("DELETE FROM translations WHERE language_code NOT IN ('en', 'ru', 'uz', 'fr')");
        jdbc.update("UPDATE translations SET source = 'SEED' WHERE source IS NULL OR source <> 'SEED'");
        synchronizer.synchronize(Instant.now());
        reset(translator);
        when(translator.configured()).thenReturn(true);
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "ru", "uz", "fr")));
        when(activeLanguages.currentActiveLanguageCodes()).thenAnswer(call -> activeLanguages.activeLanguageCodes());
    }

    @Test
    void migrationsAndCatalogAgreeSoNoSeededValueIsMistakenForAHumanEdit() {
        // Fresh database: every value the migrations V1-V11 seeded for a catalog key equals the catalog value.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM translations t JOIN translation_keys k ON k.id = t.translation_key_id"
                + " WHERE k.managed AND t.source = 'HUMAN'", Integer.class)).isZero();
    }

    @Test
    void everyCuratedLanguageServesTheWholeCatalogFromTheDatabase() throws Exception {
        for (String language : List.of("en", "ru", "uz", "fr")) {
            InterfaceCoverageService.LanguageCoverage result = coverage.coverage(language);
            assertThat(result.missingKeys()).as(language).isEmpty();
            assertThat(result.translatedKeys()).isEqualTo(catalog.keys().size());
        }
        mvc.perform(get("/api/interface-translations/fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['home.catalogTitle']").value(catalog.values("fr").get("home.catalogTitle")))
                .andExpect(jsonPath("$['admin.welcome']").value("Bienvenue"));
        assertThat(catalog.values("fr").get("home.catalogTitle")).isNotEqualTo(catalog.source().get("home.catalogTitle"));
    }

    @Test
    void languageSpecificPluralFormsAreServedButOptional() throws Exception {
        mvc.perform(get("/api/interface-translations/ru"))
                .andExpect(jsonPath("$['home.resultsCount_few']").value("{count} результата"))
                .andExpect(jsonPath("$['home.resultsCount_many']").value("{count} результатов"));
        assertThat(jdbc.queryForObject("SELECT required FROM translation_keys WHERE translation_key = 'home.resultsCount_few'",
                Boolean.class)).isFalse();
        // English has no "few" form, yet nothing is missing in English-based coverage or in a machine-translated language.
        assertThat(coverage.coverage("ru").missingKeys()).isEmpty();
        assertThat(coverage.coverage("de").missingKeys()).doesNotContain("home.resultsCount_few");
    }

    @Test
    void dictionaryResponsesCanBeRevalidatedWithAnEtag() throws Exception {
        MvcResult first = mvc.perform(get("/api/interface-translations/ru"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();
        String etag = first.getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();
        mvc.perform(get("/api/interface-translations/ru").header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void variantsInheritTheirParentLanguage() {
        InterfaceCoverageService.LanguageCoverage frFr = coverage.coverage("fr-FR");
        assertThat(frFr.missingKeys()).isEmpty();
        // Latin Uzbek is neither served to nor counted for Cyrillic Uzbek.
        assertThat(coverage.coverage("uz-Cyrl").missingKeys()).hasSize(catalog.keys().size());
        assertThat(translations.getTranslations("uz-Cyrl")).containsEntry("nav.home", catalog.source().get("nav.home"));
        assertThat(translations.getTranslations("uz-Latn-UZ")).containsEntry("nav.home", catalog.values("uz").get("nav.home"));
        InterfaceCoverageService.LanguageCoverage enGb = coverage.coverage("en-GB");
        assertThat(enGb.englishBase()).isTrue();
        assertThat(enGb.missingKeys()).isEmpty();
        assertThat(coverage.coverage("de").missingKeys()).hasSize(catalog.keys().size());
    }

    @Test
    void synchronizationKeepsHumanEditsAndEarlierAdministratorValues() {
        translations.updateTranslations("fr", Map.of("admin.welcome", "Bonjour"));
        // A value written before provenance existed that differs from the catalog: kept as a human edit.
        jdbc.update("UPDATE translations SET translation_value = 'Accueil (édité)', source = NULL WHERE language_code = 'fr'"
                + " AND translation_key_id = (SELECT id FROM translation_keys WHERE translation_key = 'nav.home')");
        // A machine value of a curated language: replaced by the curated value.
        jdbc.update("UPDATE translations SET translation_value = 'machine', source = 'MACHINE' WHERE language_code = 'fr'"
                + " AND translation_key_id = (SELECT id FROM translation_keys WHERE translation_key = 'action.save')");

        InterfaceCatalogSynchronizer.Result result = synchronizer.synchronize(Instant.now());

        assertThat(value("fr", "admin.welcome")).isEqualTo("Bonjour");
        assertThat(source("fr", "admin.welcome")).isEqualTo("HUMAN");
        assertThat(value("fr", "nav.home")).isEqualTo("Accueil (édité)");
        assertThat(source("fr", "nav.home")).isEqualTo("HUMAN");
        assertThat(value("fr", "action.save")).isEqualTo(catalog.values("fr").get("action.save"));
        assertThat(source("fr", "action.save")).isEqualTo("SEED");
        assertThat(result.machineReplaced()).isEqualTo(1);
        assertThat(result.legacyKeptAsHuman()).isEqualTo(1);
        // Idempotent.
        assertThat(synchronizer.synchronize(Instant.now()))
                .isEqualTo(new InterfaceCatalogSynchronizer.Result(0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void obsoleteLabelsAreDeactivatedByTheMigrationAndAdministratorKeysAreKept() {
        assertThat(jdbc.queryForObject("SELECT active FROM translation_keys WHERE translation_key = 'login.demoHint'",
                Boolean.class)).isFalse();
        jdbc.update("INSERT INTO translation_keys (translation_key, required, active, managed) VALUES ('custom.banner', TRUE, TRUE, FALSE)");
        synchronizer.synchronize(Instant.now());
        assertThat(jdbc.queryForObject("SELECT active FROM translation_keys WHERE translation_key = 'custom.banner'",
                Boolean.class)).isTrue();
        jdbc.update("DELETE FROM translation_keys WHERE translation_key = 'custom.banner'");
    }

    @Test
    void newLanguageIsQueuedTranslatedInBatchesAndStoredAsMachineTranslation() {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "ru", "uz", "fr", "fr-FR", "de")));
        when(translator.translate(anyList(), eq("de"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            return texts.stream().map(text -> "DE " + text).toList();
        });
        translations.updateTranslations("de", Map.of("nav.home", "Startseite"));

        assertThat(machine.scan(now)).containsExactly("de");
        InterfaceMachineTranslationService.RunResult run = machine.runDue(now);

        assertThat(run.done()).isEqualTo(1);
        verify(translator, never()).translate(anyList(), eq("fr"));
        assertThat(coverage.coverage("de").missingKeys()).isEmpty();
        assertThat(value("de", "nav.home")).isEqualTo("Startseite");
        assertThat(source("de", "nav.home")).isEqualTo("HUMAN");
        assertThat(value("de", "home.catalogTitle")).isEqualTo("DE " + catalog.source().get("home.catalogTitle"));
        assertThat(source("de", "home.catalogTitle")).isEqualTo("MACHINE");
        InterfaceTranslationJob job = jobs.findByLanguageCode("de").orElseThrow();
        assertThat(job.getStatus()).isEqualTo(InterfaceTranslationJobStatus.DONE);
        assertThat(job.getProviderCode()).isEqualTo("de");
        assertThat(job.getTranslatedKeys()).isEqualTo(catalog.keys().size() - 1);
        // Nothing left: a second scan queues nothing and the provider is not called again.
        assertThat(machine.scan(now.plusSeconds(5))).isEmpty();
    }

    @Test
    void unsupportedLanguageIsReportedWithoutBlockingSupportedOnes() {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "uz", "uz-Cyrl", "pt-BR")));
        when(translator.translate(anyList(), eq("pt"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            return texts.stream().map(text -> "PT " + text).toList();
        });

        assertThat(machine.scan(now)).containsExactlyInAnyOrder("uz-Cyrl", "pt-BR");
        machine.runDue(now);

        InterfaceTranslationJob cyrillic = jobs.findByLanguageCode("uz-Cyrl").orElseThrow();
        assertThat(cyrillic.getStatus()).isEqualTo(InterfaceTranslationJobStatus.UNSUPPORTED);
        assertThat(cyrillic.getLastError()).contains("uz-Cyrl");
        verify(translator, never()).translate(anyList(), eq("uz"));
        assertThat(jobs.findByLanguageCode("pt-BR").orElseThrow().getStatus()).isEqualTo(InterfaceTranslationJobStatus.DONE);
        assertThat(value("pt-BR", "admin.welcome")).isEqualTo("PT Welcome");
        // Unsupported languages are not queued again by later scans.
        assertThat(machine.scan(now.plusSeconds(5))).isEmpty();
    }

    @Test
    void providerFailuresBackOffKeepFinishedBatchesAndCanBeRetried() {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "it")));
        AtomicInteger calls = new AtomicInteger();
        List<Integer> sizes = new ArrayList<>();
        when(translator.translate(anyList(), eq("it"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            sizes.add(texts.size());
            if (calls.incrementAndGet() == 2) {
                throw new ProviderException(429, "Azure Translator returned HTTP 429");
            }
            return texts.stream().map(text -> "IT " + text).toList();
        });

        machine.scan(now);
        machine.runDue(now);

        InterfaceTranslationJob job = jobs.findByLanguageCode("it").orElseThrow();
        assertThat(job.getStatus()).isEqualTo(InterfaceTranslationJobStatus.PENDING);
        assertThat(job.getNextAttemptAt()).isAfterOrEqualTo(now.plus(Duration.ofMinutes(5)));
        assertThat(job.getLastError()).contains("429").doesNotContain("test-secret-key-value");
        long remaining = coverage.coverage("it").missingKeys().size();
        assertThat(remaining).isEqualTo(catalog.keys().size() - sizes.get(0));

        // Not due yet.
        assertThat(machine.runDue(now.plusSeconds(10)).claimed()).isZero();
        // The retry continues with the keys that are still missing only.
        machine.runDue(now.plus(Duration.ofMinutes(6)));
        assertThat(sizes.subList(2, sizes.size()).stream().mapToInt(Integer::intValue).sum()).isEqualTo((int) remaining);
        assertThat(jobs.findByLanguageCode("it").orElseThrow().getStatus()).isEqualTo(InterfaceTranslationJobStatus.DONE);
        assertThat(coverage.coverage("it").missingKeys()).isEmpty();
    }

    @Test
    void jobGivesUpAfterMaxAttemptsUntilAnExplicitRetry() {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "es")));
        when(translator.translate(anyList(), anyString())).thenThrow(new ProviderException(503, "Azure Translator returned HTTP 503"));

        machine.scan(now);
        machine.runDue(now);
        machine.runDue(now.plus(Duration.ofHours(1)));
        assertThat(jobs.findByLanguageCode("es").orElseThrow().getStatus()).isEqualTo(InterfaceTranslationJobStatus.FAILED);
        assertThat(machine.scan(now.plus(Duration.ofHours(2)))).isEmpty();

        InterfaceTranslationJob retried = machine.requeue("es", now.plus(Duration.ofHours(3)));
        assertThat(retried.getStatus()).isEqualTo(InterfaceTranslationJobStatus.PENDING);
        assertThat(retried.getAttempts()).isZero();
    }

    @Test
    void englishChangeMakesMachineValuesStaleButLeavesHumanOnesForReview() {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "nl")));
        when(translator.translate(anyList(), eq("nl"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            return texts.stream().map(text -> "NL " + text).toList();
        });
        translations.updateTranslations("nl", Map.of("nav.home", "Start"));
        machine.scan(now);
        machine.runDue(now);

        translations.updateTranslations("en", Map.of("home.catalogTitle", "Catalogue of services", "nav.home", "Start page"));

        InterfaceCoverageService.LanguageCoverage dutch = coverage.coverage("nl");
        assertThat(dutch.staleMachineKeys()).containsExactly("home.catalogTitle");
        assertThat(dutch.outdatedHumanKeys()).containsExactly("nav.home");
        assertThat(machine.scan(now.plusSeconds(30))).containsExactly("nl");
        machine.runDue(now.plusSeconds(30));
        assertThat(value("nl", "home.catalogTitle")).isEqualTo("NL Catalogue of services");
        assertThat(value("nl", "nav.home")).isEqualTo("Start");
    }

    @Test
    void rejectedAnswersAreNotStored() {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "pl")));
        when(translator.translate(anyList(), eq("pl"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            List<String> answers = new ArrayList<>();
            texts.forEach(text -> answers.add(text.contains("{") ? null : "PL " + text));
            return answers;
        });
        machine.scan(now);
        machine.runDue(now);
        InterfaceCoverageService.LanguageCoverage polish = coverage.coverage("pl");
        assertThat(polish.missingKeys()).isNotEmpty().allMatch(key -> catalog.source().get(key).contains("{"));
        assertThat(jobs.findByLanguageCode("pl").orElseThrow().getLastError()).contains("not accepted");
    }

    @Test
    @WithMockUser(authorities = {"TRANSLATIONS_VIEW_COVERAGE", "TRANSLATIONS_EDIT"})
    void overviewReportsCoverageAndJobsWithoutCredentials() throws Exception {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "fr", "uz-Cyrl")));
        MvcResult result = mvc.perform(get("/api/interface-translations/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerConfigured").value(false))
                .andExpect(jsonPath("$.languages[?(@.languageCode == 'fr')].percentage").value(100.0))
                .andExpect(jsonPath("$.languages[?(@.languageCode == 'uz-Cyrl')].supported").value(false))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-secret-key-value");

        mvc.perform(post("/api/interface-translations/uz_cyrl/machine-translation"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/interface-translations/de/machine-translation"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = {"TRANSLATIONS_VIEW_COVERAGE", "TRANSLATIONS_EDIT"})
    void removedLanguageComesBackWithItsTranslationsAndOnlyNewTextsGoToTheProvider() throws Exception {
        List<List<String>> sent = new ArrayList<>();
        when(translator.translate(anyList(), eq("de"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            sent.add(texts);
            return texts.stream().map(text -> "DE " + text).toList();
        });
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "de")));
        machine.scan(now);
        machine.runDue(now);
        assertThat(sent).isNotEmpty();
        sent.clear();

        // Removed from the active languages (identity-service only deactivates it): nothing is queued or deleted.
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en")));
        assertThat(machine.scan(now.plusSeconds(10))).isEmpty();
        assertThat(value("de", "home.catalogTitle")).isEqualTo("DE " + catalog.source().get("home.catalogTitle"));

        // Added again: served from the database at once, nothing to translate, the provider is not called.
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "de")));
        mvc.perform(get("/api/interface-translations/de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['home.catalogTitle']").value("DE " + catalog.source().get("home.catalogTitle")));
        assertThat(machine.scan(now.plusSeconds(20))).isEmpty();
        assertThat(machine.runDue(now.plusSeconds(20)).claimed()).isZero();
        // An explicit update of a complete language finishes without a provider call too.
        machine.requeue("DE", now.plusSeconds(30));
        assertThat(machine.runDue(now.plusSeconds(30)).done()).isEqualTo(1);
        assertThat(sent).isEmpty();

        // A label added since then is the only text sent.
        jdbc.update("INSERT INTO translation_keys (translation_key, required, active, managed) VALUES ('test.newLabel', TRUE, TRUE, FALSE)");
        translations.updateTranslations("en", Map.of("test.newLabel", "Brand new label"));
        try {
            assertThat(machine.scan(now.plusSeconds(40))).containsExactly("de");
            machine.runDue(now.plusSeconds(40));
            assertThat(sent).containsExactly(List.of("Brand new label"));
            assertThat(value("de", "test.newLabel")).isEqualTo("DE Brand new label");
        } finally {
            jdbc.update("DELETE FROM translations WHERE translation_key_id = (SELECT id FROM translation_keys WHERE translation_key = 'test.newLabel')");
            jdbc.update("DELETE FROM translation_keys WHERE translation_key = 'test.newLabel'");
        }
    }

    @Test
    void onlyMissingAndOutdatedMachineTextsAreSentToTheProvider() {
        List<String> sent = new ArrayList<>();
        when(translator.translate(anyList(), eq("sv"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            sent.addAll(texts);
            return texts.stream().map(text -> "SV " + text).toList();
        });
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "sv")));
        machine.scan(now);
        machine.runDue(now);
        assertThat(sent).hasSize(catalog.keys().size());
        sent.clear();

        // One machine value becomes outdated, one is lost, one is corrected by a person, the rest is current.
        translations.updateTranslations("en", Map.of("home.catalogTitle", "Catalogue of services"));
        translations.deleteTranslation("sv", "admin.welcome");
        translations.updateTranslations("sv", Map.of("nav.home", "Hem"));

        machine.requeue("sv", now.plusSeconds(10));
        machine.runDue(now.plusSeconds(10));

        assertThat(sent).containsExactlyInAnyOrder("Catalogue of services", catalog.source().get("admin.welcome"));
        assertThat(value("sv", "nav.home")).isEqualTo("Hem");
        assertThat(value("sv", "home.catalogTitle")).isEqualTo("SV Catalogue of services");
    }

    @Test
    void retryWhileAWorkerHoldsTheJobDoesNotStartASecondRun() {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "da")));
        machine.scan(now);
        InterfaceTranslationJob job = jobs.findByLanguageCode("da").orElseThrow();
        job.setStatus(InterfaceTranslationJobStatus.RUNNING);
        job.setAttempts(1);
        job.setNextAttemptAt(now.plus(Duration.ofMinutes(10)));
        jobs.save(job);

        InterfaceTranslationJob answer = machine.requeue("da", now.plusSeconds(5));

        assertThat(answer.getStatus()).isEqualTo(InterfaceTranslationJobStatus.RUNNING);
        assertThat(machine.runDue(now.plusSeconds(5)).claimed()).isZero();
        verify(translator, never()).translate(anyList(), anyString());
    }

    @Test
    @WithMockUser(authorities = {"TRANSLATIONS_VIEW_COVERAGE", "TRANSLATIONS_EDIT"})
    void exportAndImportRestoreTranslationsWithoutReplacingHumanEdits() throws Exception {
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en", "fi")));
        when(translator.translate(anyList(), eq("fi"))).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            return texts.stream().map(text -> "FI " + text).toList();
        });
        translations.updateTranslations("fi", Map.of("nav.home", "Etusivu"));
        machine.scan(now);
        machine.runDue(now);

        String backup = mvc.perform(get("/api/interface-translations/FI/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("interface-translations-fi.json")))
                .andExpect(jsonPath("$.format").value("reestr-interface-translations/1"))
                .andExpect(jsonPath("$.languageCode").value("fi"))
                .andExpect(jsonPath("$.entries[?(@.key == 'nav.home')].source").value("HUMAN"))
                .andExpect(jsonPath("$.entries[?(@.key == 'admin.welcome')].source").value("MACHINE"))
                .andReturn().getResponse().getContentAsString();

        // Disaster: the rows are gone. Meanwhile a person writes a new home label.
        jdbc.update("DELETE FROM translations WHERE language_code = 'fi'");
        translations.updateTranslations("fi", Map.of("nav.home", "Kotisivu"));

        mvc.perform(post("/api/interface-translations/fi/import").contentType("application/json").content(backup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(catalog.keys().size() - 1))
                .andExpect(jsonPath("$.keptHuman").value(1));
        assertThat(value("fi", "admin.welcome")).isEqualTo("FI Welcome");
        assertThat(source("fi", "admin.welcome")).isEqualTo("MACHINE");
        assertThat(value("fi", "nav.home")).isEqualTo("Kotisivu");
        assertThat(coverage.coverage("fi").missingKeys()).isEmpty();
        // Restored machine values are current, so nothing is sent to the provider again.
        reset(translator);
        when(translator.configured()).thenReturn(true);
        assertThat(machine.scan(now.plusSeconds(60))).isEmpty();
        verify(translator, never()).translate(anyList(), anyString());

        // Explicitly restoring the file's human values replaces the newer one; a file for another language is refused.
        mvc.perform(post("/api/interface-translations/fi/import").param("replaceHuman", "true")
                        .contentType("application/json").content(backup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));
        assertThat(value("fi", "nav.home")).isEqualTo("Etusivu");
        mvc.perform(post("/api/interface-translations/sv/import").contentType("application/json").content(backup))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/interface-translations/fi/import").contentType("application/json").content("{\"format\":\"other\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportBundleCoversRemovedLanguagesToo() {
        translations.updateTranslations("it", Map.of("nav.home", "Home page"));
        when(activeLanguages.activeLanguageCodes()).thenReturn(Optional.of(Set.of("en")));

        var bundle = transfer.exportAll(now);

        assertThat(bundle.languages()).extracting(InterfaceTranslationExport::languageCode).contains("en", "ru", "uz", "fr", "it");
    }

    private String value(String language, String key) {
        return jdbc.queryForObject("SELECT t.translation_value FROM translations t JOIN translation_keys k ON k.id = t.translation_key_id"
                + " WHERE t.language_code = ? AND k.translation_key = ?", String.class, language, key);
    }

    private String source(String language, String key) {
        return jdbc.queryForObject("SELECT t.source FROM translations t JOIN translation_keys k ON k.id = t.translation_key_id"
                + " WHERE t.language_code = ? AND k.translation_key = ?", String.class, language, key);
    }
}
