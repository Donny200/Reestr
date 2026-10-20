package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.config.ReportProperties;
import adliya.uz.functioncatalogservice.config.SecurityConfig;
import adliya.uz.functioncatalogservice.dto.AssignmentOverview;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.PublicChangeType;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import adliya.uz.functioncatalogservice.security.JwtAuthenticationFilter;
import adliya.uz.functioncatalogservice.security.SimpleJwtService;
import adliya.uz.functioncatalogservice.service.ClientAddressResolver;
import adliya.uz.functioncatalogservice.service.FeedbackService;
import adliya.uz.functioncatalogservice.service.FunctionAssignmentService;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.PublicChangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {FeedbackController.class, FunctionAssignmentController.class, OrgFunctionController.class},
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ReportProperties.class, ClientAddressResolver.class})
class FeedbackAndAssignmentEndpointSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean FeedbackService feedback;
    @MockitoBean FunctionAssignmentService assignments;
    @MockitoBean OrgFunctionService functions;
    @MockitoBean PublicChangeService publicChanges;
    @MockitoBean adliya.uz.functioncatalogservice.service.MachineTranslationQueue translations;
    @MockitoBean SimpleJwtService jwt;
    @MockitoBean adliya.uz.functioncatalogservice.service.ServiceGuideService guides;

    @Test void anonymousVisitorsMayVoteAndReadPublicHistoryButNotAssignments() throws Exception {
        when(publicChanges.publicHistory(4L)).thenReturn(List.of());
        mvc.perform(post("/api/feedback").header("X-Real-IP", "203.0.113.7").contentType("application/json")
                .content("{\"serviceId\":4,\"helpful\":true}")).andExpect(status().isAccepted());
        verify(feedback).record(any(), eq("203.0.113.7"));
        mvc.perform(get("/api/functions/4/history")).andExpect(status().isOk());
        mvc.perform(get("/api/functions/assignments")).andExpect(status().isForbidden());
        mvc.perform(get("/api/functions/4/assignee/candidates")).andExpect(status().isForbidden());
        mvc.perform(put("/api/functions/4/assignee").contentType("application/json").content("{\"userId\":7}")).andExpect(status().isForbidden());
        verifyNoInteractions(assignments);
    }

    @Test void assignmentWritesRequireTheAssignPermissionAndReadsAnAdministrativePermission() throws Exception {
        var viewer = user("viewer").authorities(new SimpleGrantedAuthority("FUNCTIONS_VIEW"));
        var assigner = user("assigner").authorities(new SimpleGrantedAuthority("FUNCTIONS_ASSIGN"));
        var reporter = user("reporter").authorities(new SimpleGrantedAuthority("REPORTS_VIEW"));
        when(assignments.overview(any(), any())).thenReturn(new AssignmentOverview(new AssignmentOverview.Counts(0, 0, 0), true, List.of()));
        OrgFunction card = OrgFunction.builder().id(4L).name("Passport").organizationId(10L).build();
        when(assignments.assign(4L, 7L)).thenReturn(card);
        when(assignments.unassign(4L)).thenReturn(card);

        mvc.perform(get("/api/functions/assignments").with(reporter)).andExpect(status().isForbidden());
        mvc.perform(get("/api/functions/assignments").with(viewer)).andExpect(status().isOk());
        mvc.perform(put("/api/functions/4/assignee").with(viewer).contentType("application/json").content("{\"userId\":7}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/functions/4/assignee").with(viewer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/functions/4/assignee").with(assigner).contentType("application/json").content("{\"userId\":7}")).andExpect(status().isOk());
        mvc.perform(put("/api/functions/4/assignee").with(assigner).contentType("application/json").content("{\"userId\":0}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/functions/4/assignee").with(assigner)).andExpect(status().isOk());
        mvc.perform(get("/api/functions/assignments").param("state", "BROKEN").with(assigner)).andExpect(status().isBadRequest());
    }

    @Test void publicHistoryExposesOnlyDateTypeAndSectionsAndThrottledVotesGetRetryAfter() throws Exception {
        when(publicChanges.publicHistory(4L)).thenReturn(List.of(
                new adliya.uz.functioncatalogservice.entity.FunctionPublicChange(1L, 4L, Instant.parse("2026-10-01T00:00:00Z"), PublicChangeType.UPDATE,
                        List.of("fee"), java.util.Map.of("fee", "abc"))));
        mvc.perform(get("/api/functions/4/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changeType").value("UPDATE"))
                .andExpect(jsonPath("$[0].sections[0]").value("fee"))
                .andExpect(jsonPath("$[0].sectionHashes").doesNotExist())
                .andExpect(jsonPath("$[0].id").doesNotExist());
        doThrow(new RateLimitExceededException(Duration.ofSeconds(45))).when(feedback).record(any(), any());
        mvc.perform(post("/api/feedback").contentType("application/json").content("{\"serviceId\":4,\"helpful\":false}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "45"));
    }
}
