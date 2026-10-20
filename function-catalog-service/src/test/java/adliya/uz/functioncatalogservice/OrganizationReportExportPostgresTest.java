package adliya.uz.functioncatalogservice;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.EngagementDailyCount;
import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ReportCategory;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.entity.ReportStatus;
import adliya.uz.functioncatalogservice.repository.EngagementDailyCountRepository;
import adliya.uz.functioncatalogservice.repository.InformationReportRepository;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import adliya.uz.functioncatalogservice.service.InterfaceLabels;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.TranslationClient;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static adliya.uz.functioncatalogservice.support.TestPrincipals.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Excel and PDF exports: real files, the dashboard filters, organization scope, and no visitor personal data. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.import=", "jwt.secret=export-test-secret-at-least-32-bytes-long",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "catalog.seed-legacy.enabled=false", "catalog.seed-editorial.enabled=false", "catalog.scheduling.enabled=false"
})
@AutoConfigureMockMvc
class OrganizationReportExportPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String CONTACT = "visitor.secret@example.test";
    private static final String DESCRIPTION = "The fee on this page is wrong, my passport AA1234567";
    private static final UsernamePasswordAuthenticationToken ANALYST_10 = staff(10, List.of(10L), "ORG_ANALYTICS_VIEW", "REPORTS_VIEW");
    private static final UsernamePasswordAuthenticationToken ANALYST_10_NO_REPORTS = staff(11, List.of(10L), "ORG_ANALYTICS_VIEW");
    private static final UsernamePasswordAuthenticationToken ADMIN = superAdmin("ORG_ANALYTICS_VIEW", "REPORTS_VIEW", "ORG_REPORTS_MANAGE");

    @Autowired OrgFunctionService functions;
    @Autowired EngagementDailyCountRepository counts;
    @Autowired InformationReportRepository reports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean IdentityOrganizationClient organizations;
    @MockitoBean TranslationClient translator;
    @MockitoBean InterfaceLabels labels;

    private OrgFunction passport;
    private OrgFunction foreign;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE engagement_daily_counts, feedback_daily_counts, information_reports, audit_log_functions, audit_logs, "
                + "function_public_changes, translation_jobs, org_functions, function_categories RESTART IDENTITY CASCADE");
        doAnswer(call -> null).when(organizations).requireExisting(any());
        when(organizations.publicDirectory()).thenReturn(Map.of(
                10L, new IdentityOrganizationClient.PublicOrganization(10L, "Justice office"),
                20L, new IdentityOrganizationClient.PublicOrganization(20L, "Tax office")));
        when(labels.forLanguage(any())).thenAnswer(call -> new InterfaceLabels.Labels("en", Map.of()));
        when(labels.forLanguage("ru")).thenReturn(new InterfaceLabels.Labels("ru", Map.of(
                "export.sheet.summary", "Сводка", "export.sheet.activity", "Активность по услугам",
                "export.report.title", "Отчёт организации")));

        passport = published("Passport registration", 10L);
        foreign = published("Foreign tax certificate", 20L);
        LocalDate day = LocalDate.of(2026, 3, 5);
        row(day, EngagementEventType.SERVICE_VIEW, 10L, passport.getId(), 7);
        row(day, EngagementEventType.PRINT, 10L, passport.getId(), 2);
        row(day.minusDays(10), EngagementEventType.SERVICE_VIEW, 10L, passport.getId(), 4);
        row(day, EngagementEventType.SERVICE_VIEW, 20L, foreign.getId(), 50);
        report(passport, "2026-03-04T08:00:00Z");
        report(foreign, "2026-03-04T09:00:00Z");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void excelWorkbookHasTheNamedSheetsTypedFiguresAndOnlyTheCallersOrganization() throws Exception {
        byte[] file = mvc.perform(report().with(authentication(ANALYST_10)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("organization-report_2026-03-01_2026-03-10.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            List<String> sheets = new ArrayList<>();
            workbook.forEach(sheet -> sheets.add(sheet.getSheetName()));
            assertThat(sheets).containsExactly("Summary", "Service activity", "Activity over time", "Quality issues", "Visitor reports");

            String summary = text(workbook.getSheet("Summary"));
            assertThat(summary).contains("Justice office", "2026-03-01 – 2026-03-10", "2026-02-19 – 2026-02-28", "Service page views");
            Row views = find(workbook.getSheet("Summary"), "Service page views");
            assertThat(views.getCell(1).getNumericCellValue()).isEqualTo(7);
            assertThat(views.getCell(2).getNumericCellValue()).isEqualTo(4);
            assertThat(views.getCell(3).getNumericCellValue()).isEqualTo(0.75);

            Row activity = find(workbook.getSheet("Service activity"), "Passport registration");
            assertThat(activity.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(activity.getCell(3).getStringCellValue()).isEqualTo("Justice office");
            assertThat(text(workbook.getSheet("Activity over time"))).contains("Service page views");
            assertThat(workbook.getSheet("Activity over time").getLastRowNum()).isGreaterThanOrEqualTo(10);
            assertThat(text(workbook.getSheet("Quality issues"))).contains("Passport registration", "Verification overdue");
            assertThat(text(workbook.getSheet("Visitor reports"))).contains("Passport registration", "New", "Documents or steps are wrong");

            String everything = text(workbook);
            assertThat(everything).doesNotContain("Foreign tax certificate", "Tax office", CONTACT, DESCRIPTION, "AA1234567");
        }
    }

    @Test void staffCannotExportAnotherOrganizationAndAnonymousCannotExportAtAll() throws Exception {
        mvc.perform(report().param("organizationId", "20").with(authentication(ANALYST_10))).andExpect(status().isForbidden());
        mvc.perform(report().param("format", "PDF").param("organizationId", "20").with(authentication(ANALYST_10))).andExpect(status().isForbidden());
        mvc.perform(report().with(anonymous())).andExpect(status().isForbidden());
        mvc.perform(report().with(authentication(staff(12, List.of(10L), "REPORTS_VIEW", "FUNCTIONS_VIEW")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/export").param("format", "XLSX").param("organizationId", "20").with(authentication(ANALYST_10)))
                .andExpect(status().isForbidden());
    }

    @Test void superAdminMayExportAcrossOrganizations() throws Exception {
        try (XSSFWorkbook workbook = workbook(report().with(authentication(ADMIN)))) {
            String everything = text(workbook);
            assertThat(everything).contains("All organizations", "Passport registration", "Foreign tax certificate", "Tax office");
            assertThat(everything).doesNotContain(CONTACT, DESCRIPTION);
        }
        try (XSSFWorkbook workbook = workbook(report().param("organizationId", "20").with(authentication(ADMIN)))) {
            assertThat(text(workbook)).contains("Foreign tax certificate").doesNotContain("Passport registration");
        }
    }

    @Test void visitorReportsAreLeftOutForRolesThatCannotViewThem() throws Exception {
        try (XSSFWorkbook workbook = workbook(report().with(authentication(ANALYST_10_NO_REPORTS)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheet("Visitor reports")).isNull();
            assertThat(text(workbook)).contains("Visitor reports are not included because your role cannot view them.");
        }
    }

    @Test void sheetNamesAndLabelsFollowTheRequestedLanguage() throws Exception {
        try (XSSFWorkbook workbook = workbook(report().param("lang", "ru").with(authentication(ANALYST_10)))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Сводка");
            assertThat(workbook.getSheetName(1)).isEqualTo("Активность по услугам");
            assertThat(text(workbook.getSheetAt(0))).contains("Отчёт организации");
        }
    }

    @Test void pdfIsARealPrintableReportWithTheSameScopeAndNoPersonalData() throws Exception {
        byte[] pdf = mvc.perform(report().param("format", "PDF").with(authentication(ANALYST_10)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = pdfText(pdf);
        assertThat(text).contains("Organization report", "Justice office", "Summary", "Service activity", "Activity over time",
                "Quality issues", "Visitor reports", "Passport registration");
        assertThat(text).doesNotContain("Foreign tax certificate", CONTACT, DESCRIPTION);
    }

    @Test void visitorReportListExportsAsExcelAndPdfWhileCsvKeepsWorking() throws Exception {
        MockHttpServletRequestBuilder base = get("/api/reports/export").param("status", "ALL").param("from", "2026-03-01").param("to", "2026-03-10");
        try (XSSFWorkbook workbook = workbook(get("/api/reports/export").param("status", "ALL").param("format", "XLSX")
                .with(authentication(ANALYST_10)))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Visitor reports");
            assertThat(text(workbook)).contains("Passport registration", "All").doesNotContain(CONTACT, DESCRIPTION, "Foreign tax certificate");
        }
        String pdf = pdfText(mvc.perform(get("/api/reports/export").param("status", "ALL").param("format", "PDF").with(authentication(ANALYST_10)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(pdf).contains("Passport registration").doesNotContain(CONTACT, DESCRIPTION);

        String csv = mvc.perform(base.with(authentication(ANALYST_10)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿report_id,").contains("Passport registration").doesNotContain(CONTACT);
    }

    private MockHttpServletRequestBuilder report() {
        return get("/api/analytics/report/export").param("from", "2026-03-01").param("to", "2026-03-10");
    }

    private XSSFWorkbook workbook(MockHttpServletRequestBuilder request) throws Exception {
        byte[] file = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(file));
    }

    private static Row find(Sheet sheet, String label) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && label.equals(cell.getStringCellValue())) {
                    return row;
                }
            }
        }
        throw new AssertionError("No row with " + label + " in " + sheet.getSheetName());
    }

    private static String text(XSSFWorkbook workbook) {
        List<String> parts = new ArrayList<>();
        workbook.forEach(sheet -> parts.add(text(sheet)));
        return String.join("\n", parts);
    }

    private static String text(Sheet sheet) {
        List<String> cells = new ArrayList<>();
        for (Row row : sheet) {
            for (Cell cell : row) {
                cells.add(cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString());
            }
        }
        return cells.stream().collect(Collectors.joining(" | "));
    }

    private static String pdfText(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
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

    private void row(LocalDate day, EngagementEventType type, Long organizationId, Long functionId, long count) {
        counts.save(EngagementDailyCount.builder().activityDate(day).eventType(type).organizationId(organizationId)
                .functionId(functionId).eventCount(count).build());
    }

    private void report(OrgFunction card, String createdAt) {
        Instant at = Instant.parse(createdAt);
        reports.save(InformationReport.builder().entityType(ReportEntityType.FUNCTION).entityId(card.getId()).entityLabel(card.getName())
                .organizationId(card.getOrganizationId()).category(ReportCategory.DOCUMENTS_OR_STEPS).description(DESCRIPTION)
                .contact(CONTACT).language("en").status(ReportStatus.NEW).createdAt(at).updatedAt(at).build());
    }

    private OrgFunction published(String name, Long organization) {
        login(superAdmin(with(EDITOR)));
        var card = functions.create(new CreateOrgFunctionRequest(name, "Description", organization, "Docs", null));
        functions.submitForReview(card.getId());
        OrgFunction result = functions.publish(card.getId());
        SecurityContextHolder.clearContext();
        return result;
    }
}
