package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.config.ReportProperties;
import adliya.uz.functioncatalogservice.config.SecurityConfig;
import adliya.uz.functioncatalogservice.entity.*;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import adliya.uz.functioncatalogservice.security.JwtAuthenticationFilter;
import adliya.uz.functioncatalogservice.security.SimpleJwtService;
import adliya.uz.functioncatalogservice.service.AnalyticsExportService;
import adliya.uz.functioncatalogservice.service.ClientAddressResolver;
import adliya.uz.functioncatalogservice.service.CsvExport;
import adliya.uz.functioncatalogservice.service.FunctionVerificationService;
import adliya.uz.functioncatalogservice.service.ReportReviewService;
import adliya.uz.functioncatalogservice.service.ReportSubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {InformationReportController.class, FunctionVerificationController.class},
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ReportProperties.class, ClientAddressResolver.class})
class ReportAndVerificationEndpointSecurityTest {

    private static final String VALID_REPORT = """
            {"entityType":"FUNCTION","entityId":5,"category":"OUTDATED_INFORMATION",
             "description":"The fee listed here changed last month.","contact":"","language":"ru"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean ReportSubmissionService submissions;
    @MockitoBean ReportReviewService reviews;
    @MockitoBean FunctionVerificationService verification;
    @MockitoBean AnalyticsExportService exports;
    @MockitoBean adliya.uz.functioncatalogservice.service.OrganizationReportService reportExports;
    @MockitoBean SimpleJwtService jwt;
    @MockitoBean adliya.uz.functioncatalogservice.security.CatalogAccess access;

    @Test void anonymousVisitorCanSubmitAndTheForwardedClientAddressIsUsed() throws Exception {
        mvc.perform(post("/api/reports").header("X-Real-IP", "203.0.113.9").contentType("application/json").content(VALID_REPORT))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.received").value(true));
        verify(submissions).submit(any(), eq("203.0.113.9"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"entityType\":\"FUNCTION\",\"entityId\":5,\"category\":\"OTHER\",\"description\":\"short\"}",
            "{\"entityType\":\"FUNCTION\",\"entityId\":5,\"category\":\"OTHER\",\"description\":\"Long enough description\",\"contact\":\"not a contact\"}",
            "{\"entityType\":\"FUNCTION\",\"entityId\":-1,\"category\":\"OTHER\",\"description\":\"Long enough description\"}",
            "{\"entityType\":\"FUNCTION\",\"entityId\":5,\"description\":\"Long enough description\"}",
            "{\"entityType\":\"DRAFT\",\"entityId\":5,\"category\":\"OTHER\",\"description\":\"Long enough description\"}",
            "{\"entityType\":\"FUNCTION\",\"entityId\":5,\"category\":\"OTHER\",\"description\":\"Long enough description\",\"language\":\"not a language\"}"
    })
    void invalidSubmissionsAreRejectedBeforeReachingTheService(String body) throws Exception {
        mvc.perform(post("/api/reports").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(submissions);
    }

    @Test void rateLimitedSubmissionReturns429WithRetryAfter() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofMinutes(5))).when(submissions).submit(any(), any());
        mvc.perform(post("/api/reports").contentType("application/json").content(VALID_REPORT))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "300"));
    }

    @Test void reviewEndpointsRequireReportPermissionsNotJustAuthentication() throws Exception {
        mvc.perform(get("/api/reports")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports").with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/1").with(user("editor").authorities(new SimpleGrantedAuthority("FUNCTIONS_VIEW"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/reports/1/status").contentType("application/json").content("{\"status\":\"RESOLVED\"}")
                .with(user("viewer").authorities(new SimpleGrantedAuthority("REPORTS_VIEW")))).andExpect(status().isForbidden());
        verifyNoInteractions(reviews);

        when(reviews.list(any())).thenReturn(List.of(report()));
        when(reviews.changeStatus(eq(1L), eq(ReportStatus.RESOLVED), any())).thenReturn(report());
        mvc.perform(get("/api/reports?status=open").with(user("viewer").authorities(new SimpleGrantedAuthority("REPORTS_VIEW"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].entityLabel").value("Passport renewal"));
        mvc.perform(put("/api/reports/1/status").contentType("application/json").content("{\"status\":\"RESOLVED\",\"note\":\"Fixed\"}")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REPORTS_VIEW"), new SimpleGrantedAuthority("REPORTS_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test void organizationReportManagersCanViewAndChangeStatusWithoutLegacyPermissions() throws Exception {
        var manager = user("org-manager").authorities(new SimpleGrantedAuthority("ORG_REPORTS_MANAGE"));
        when(reviews.list(any())).thenReturn(List.of(report()));
        when(reviews.changeStatus(eq(1L), eq(ReportStatus.REJECTED), eq("Not a catalog error"))).thenReturn(report());
        mvc.perform(get("/api/reports").with(manager)).andExpect(status().isOk());
        mvc.perform(get("/api/reports/1/history").with(manager)).andExpect(status().isOk());
        mvc.perform(put("/api/reports/1/status").contentType("application/json")
                        .content("{\"status\":\"REJECTED\",\"note\":\"Not a catalog error\"}").with(manager))
                .andExpect(status().isOk());
    }

    @Test void legacyManagePermissionStillRequiresViewPermission() throws Exception {
        mvc.perform(put("/api/reports/1/status").contentType("application/json").content("{\"status\":\"RESOLVED\"}")
                .with(user("legacy").authorities(new SimpleGrantedAuthority("REPORTS_MANAGE")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/1/history").with(user("editor").authorities(new SimpleGrantedAuthority("FUNCTIONS_VIEW"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reviews);
    }

    @Test void reportExportUsesTheViewPermissionAndReturnsAnExcelFriendlyAttachment() throws Exception {
        mvc.perform(get("/api/reports/export")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/export").with(user("editor").authorities(new SimpleGrantedAuthority("FUNCTIONS_VIEW"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(exports);

        when(exports.reports(any(), eq(CsvExport.Delimiter.SEMICOLON))).thenReturn(new CsvExport.File("visitor-reports_2026-03-01.csv",
                CsvExport.write(CsvExport.Delimiter.SEMICOLON, List.of("report_id"), List.of(List.of(1L))), true));
        mvc.perform(get("/api/reports/export").param("delimiter", "SEMICOLON").param("status", "ALL")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("REPORTS_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=")))
                .andExpect(header().string("X-Export-Truncated", "true"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/reports/export").param("delimiter", "TAB")
                .with(user("viewer").authorities(new SimpleGrantedAuthority("REPORTS_VIEW")))).andExpect(status().isBadRequest());
    }

    @Test void unknownStatusValuesAreRejected() throws Exception {
        mvc.perform(put("/api/reports/1/status").contentType("application/json").content("{\"status\":\"DISMISSED\"}")
                .with(user("org-manager").authorities(new SimpleGrantedAuthority("ORG_REPORTS_MANAGE")))).andExpect(status().isBadRequest());
        verifyNoInteractions(reviews);
    }

    @Test void verificationRequiresReviewPermission() throws Exception {
        mvc.perform(post("/api/functions/3/verify")).andExpect(status().isForbidden());
        mvc.perform(post("/api/functions/3/verify").with(user("editor").authorities(new SimpleGrantedAuthority("FUNCTIONS_EDIT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verification);
        when(verification.verify(3L)).thenReturn(OrgFunction.builder().id(3L).name("Card").organizationId(10L)
                .officialSourceUrl("https://gov.example/service").lastVerifiedAt(Instant.now()).verifiedByUserId(42L).build());
        mvc.perform(post("/api/functions/3/verify").with(user("reviewer").authorities(new SimpleGrantedAuthority("FUNCTIONS_REVIEW"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedByUserId").value(42));
    }

    private static InformationReport report() {
        return InformationReport.builder().id(1L).entityType(ReportEntityType.FUNCTION).entityId(5L).entityLabel("Passport renewal")
                .organizationId(10L).category(ReportCategory.OTHER).description("Details").status(ReportStatus.NEW)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
