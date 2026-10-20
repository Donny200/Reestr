package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.AuditLog;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.repository.AuditLogRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "jwt.secret=" + AdminFunctionsBrowserIT.JWT_SECRET,
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false"
})
class AdminFunctionsBrowserIT {

    static final String JWT_SECRET = "browser-isolated-test-secret-at-least-32-bytes";

    private static final Logger LOG = LoggerFactory.getLogger(AdminFunctionsBrowserIT.class);
    private static final long OWN_ORGANIZATION = 10L;
    private static final long FOREIGN_ORGANIZATION = 20L;
    private static final List<String> EDITOR_PERMISSIONS = List.of(
            "FUNCTIONS_VIEW", "FUNCTIONS_CREATE", "FUNCTIONS_EDIT", "FUNCTIONS_DEACTIVATE", "FUNCTIONS_SUBMIT_REVIEW",
            "FUNCTIONS_REVIEW", "FUNCTIONS_PUBLISH", "FUNCTIONS_REACTIVATE", "FUNCTIONS_TRANSLATIONS_EDIT", "AUDIT_VIEW");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired OrgFunctionRepository functions;
    @Autowired AuditLogRepository audit;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @TempDir Path temporary;

    @Test
    void browserUsesRealControllerJwtTransactionsAndPostgres() throws Exception {
        OrgFunction foreign = functions.saveAndFlush(OrgFunction.builder()
                .name("Foreign draft")
                .description("Other organization")
                .organizationId(FOREIGN_ORGANIZATION)
                .status(FunctionStatus.DRAFT)
                .build());
        doAnswer(call -> {
            Long id = call.getArgument(0);
            if (id == null || (id != OWN_ORGANIZATION && id != FOREIGN_ORGANIZATION)) {
                throw new IllegalArgumentException("Unknown organization");
            }
            return null;
        }).when(organizations).requireExisting(any());

        Path log = temporary.resolve("browser.log");
        Process running = browserTests(foreign.getId(), log).start();
        try {
            boolean finished = running.waitFor(4, TimeUnit.MINUTES);
            assertThat(finished).withFailMessage("Browser test timed out: %s", Files.readString(log)).isTrue();
            assertThat(running.exitValue()).withFailMessage("Browser test failed: %s", Files.readString(log)).isZero();
            LOG.debug("Browser test output:\n{}", Files.readString(log));
        } finally {
            if (running.isAlive()) {
                running.descendants().forEach(ProcessHandle::destroyForcibly);
                running.destroyForcibly();
            }
        }

        OrgFunction created = functions.findAll().stream()
                .filter(function -> function.getName().equals("Browser service"))
                .findFirst()
                .orElseThrow();
        assertThat(created.getStatus()).isEqualTo(FunctionStatus.DEACTIVATED);
        assertThat(created.getNameTranslations().get("ru")).isEqualTo(new TranslatedText("Услуга браузера", "human"));
        assertThat(created.getNameTranslations().get("uz").text()).isEqualTo("Brauzer xizmati");
        assertThat(audit.findFunctionHistory(created.getId())).extracting(AuditLog::getAction)
                .contains(AuditAction.CREATE, AuditAction.TRANSLATION_EDIT, AuditAction.SUBMIT_REVIEW,
                        AuditAction.PUBLISH, AuditAction.DEACTIVATE);
    }

    private ProcessBuilder browserTests(Long foreignId, Path log) {
        Path front = Path.of("..", "front").toAbsolutePath().normalize();
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder process = new ProcessBuilder(windows
                ? List.of("cmd.exe", "/d", "/c", "npm.cmd", "run", "test:backend")
                : List.of("npm", "run", "test:backend"));
        process.directory(front.toFile());
        process.environment().put("CATALOG_TEST_URL", "http://127.0.0.1:" + port);
        process.environment().put("CATALOG_TEST_JWT", token(true));
        process.environment().put("CATALOG_SCOPED_JWT", token(false));
        process.environment().put("CATALOG_FOREIGN_ID", foreignId.toString());
        return process.redirectErrorStream(true).redirectOutput(log.toFile());
    }

    private static String token(boolean global) {
        return Jwts.builder()
                .subject("editor@example.test")
                .claim("userId", 42L)
                .claim("role", global ? "ROLE_SUPER_ADMIN" : "ROLE_ORG_ADMIN")
                .claim("organizationIds", List.of(OWN_ORGANIZATION))
                .claim("permissions", EDITOR_PERMISSIONS)
                .claim("mustChangePassword", false)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
