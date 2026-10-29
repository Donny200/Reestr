package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.config.SecurityConfig;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.security.JwtAuthenticationFilter;
import adliya.uz.functioncatalogservice.security.SimpleJwtService;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OrgFunctionController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class OrgFunctionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrgFunctionService orgFunctionService;

    @MockitoBean
    private SimpleJwtService jwtService;

    @MockitoBean
    private adliya.uz.functioncatalogservice.service.PublicChangeService publicChanges;

    @MockitoBean
    private adliya.uz.functioncatalogservice.service.MachineTranslationQueue translations;

    @MockitoBean
    private adliya.uz.functioncatalogservice.service.ServiceGuideService guides;

    @MockitoBean
    private adliya.uz.functioncatalogservice.service.FunctionReviewService reviews;

    @Test
    void publicListRemainsAnonymousAndReturnsOnlyServicePublicResult() throws Exception {
        when(orgFunctionService.getAll()).thenReturn(List.of(function(1L, true)));

        mockMvc.perform(get("/api/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].active").value(true));

        verify(orgFunctionService).getAll();
    }

    @Test
    void publicResponsesExposeInstructionsButNotTheVerifyingStaffMember() throws Exception {
        OrgFunction verified = function(4L, true);
        verified.setFee("Free of charge");
        verified.setOfficialSourceUrl("https://gov.example/service");
        verified.setLastVerifiedAt(java.time.Instant.now());
        verified.setVerifiedByUserId(42L);
        when(orgFunctionService.getById(4L)).thenReturn(verified);

        mockMvc.perform(get("/api/functions/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions.fee").value("Free of charge"))
                .andExpect(jsonPath("$.instructions.steps").doesNotExist())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.officialSourceUrl").value("https://gov.example/service"))
                .andExpect(jsonPath("$.verifiedByUserId").doesNotExist());
    }

    @Test
    void adminListIsNotMadePublicByBroadGetMatcher() throws Exception {
        mockMvc.perform(get("/api/functions/admin"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orgFunctionService);
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_VIEW")
    void functionsViewCanReadAdminListIncludingInactiveState() throws Exception {
        when(orgFunctionService.getAllForAdmin()).thenReturn(List.of(function(2L, false)));

        mockMvc.perform(get("/api/functions/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));

        verify(orgFunctionService).getAllForAdmin();
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_CREATE")
    void functionsCreateCanCreate() throws Exception {
        when(orgFunctionService.create(any())).thenReturn(function(3L, true));

        mockMvc.perform(post("/api/functions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Registration",
                                  "organizationId": 10
                                }
                                """))
                .andExpect(status().isCreated());

        verify(orgFunctionService).create(any());
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_EDIT")
    void functionsEditCanUpdate() throws Exception {
        when(orgFunctionService.update(eq(3L), any())).thenReturn(function(3L, true));

        mockMvc.perform(put("/api/functions/3")
                        .contentType("application/json")
                        .content("{\"name\":\"Updated registration\"}"))
                .andExpect(status().isOk());

        verify(orgFunctionService).update(eq(3L), any());
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_MANAGE_REQUIREMENTS")
    void manageRequirementsCanUpdateRequirements() throws Exception {
        when(orgFunctionService.updateRequirements(3L, "Passport")).thenReturn(function(3L, true));

        mockMvc.perform(put("/api/functions/3/requirements")
                        .contentType("application/json")
                        .content("{\"requirements\":\"Passport\"}"))
                .andExpect(status().isOk());

        verify(orgFunctionService).updateRequirements(3L, "Passport");
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_EDIT")
    void functionsEditOpensAndDiscardsDraftRevisions() throws Exception {
        OrgFunction revision = function(9L, false);
        revision.setStatus(adliya.uz.functioncatalogservice.entity.FunctionStatus.DRAFT);
        revision.setRevisionOfId(3L);
        when(orgFunctionService.createRevision(3L)).thenReturn(revision);

        mockMvc.perform(post("/api/functions/3/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.revisionOfId").value(3));
        mockMvc.perform(delete("/api/functions/3/revision"))
                .andExpect(status().isNoContent());

        verify(orgFunctionService).createRevision(3L);
        verify(orgFunctionService).discardRevision(3L);
    }

    @Test
    @WithMockUser(authorities = {"FUNCTIONS_VIEW", "FUNCTIONS_SUBMIT_REVIEW", "FUNCTIONS_PUBLISH"})
    void revisionsRequireTheEditPermissionNotJustWorkflowRights() throws Exception {
        mockMvc.perform(post("/api/functions/3/revisions")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/functions/3/revision")).andExpect(status().isForbidden());
        verify(orgFunctionService, never()).createRevision(any());
        verify(orgFunctionService, never()).discardRevision(any());
    }

    @Test
    void revisionEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(post("/api/functions/3/revisions")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/functions/3/revision")).andExpect(status().isForbidden());
        verifyNoInteractions(orgFunctionService);
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_VIEW")
    void adminDetailCarriesThePendingRevisionWhilePublicDetailDoesNot() throws Exception {
        OrgFunction published = function(4L, true);
        OrgFunction revision = function(8L, false);
        revision.setStatus(adliya.uz.functioncatalogservice.entity.FunctionStatus.PENDING_REVIEW);
        revision.setRevisionOfId(4L);
        when(orgFunctionService.getForAdmin(4L)).thenReturn(published);
        when(orgFunctionService.draftRevisionsOf(List.of(published))).thenReturn(java.util.Map.of(4L, revision));
        when(orgFunctionService.getById(4L)).thenReturn(published);

        mockMvc.perform(get("/api/functions/4/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision.id").value(8))
                .andExpect(jsonPath("$.revision.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.revisionOfId").doesNotExist());
        mockMvc.perform(get("/api/functions/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.revisionOfId").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_DEACTIVATE")
    void functionsDeactivateCanDeactivate() throws Exception {
        mockMvc.perform(delete("/api/functions/3"))
                .andExpect(status().isNoContent());

        verify(orgFunctionService).deactivate(3L);
    }

    @Test
    @WithMockUser(authorities = "FUNCTIONS_VIEW")
    void unrelatedPermissionCannotDeactivate() throws Exception {
        mockMvc.perform(delete("/api/functions/3"))
                .andExpect(status().isForbidden());

        verify(orgFunctionService, never()).deactivate(3L);
    }

    private OrgFunction function(Long id, boolean active) {
        return OrgFunction.builder()
                .id(id)
                .name("Function " + id)
                .organizationId(10L)
                .status(active ? adliya.uz.functioncatalogservice.entity.FunctionStatus.PUBLISHED : adliya.uz.functioncatalogservice.entity.FunctionStatus.DEACTIVATED)
                .build();
    }
}
