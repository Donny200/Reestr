package adliya.uz.task1.controller;

import adliya.uz.task1.config.CorsProperties;
import adliya.uz.task1.config.SecurityConfig;
import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.security.CustomUserDetailsService;
import adliya.uz.task1.config.security.JwtAuthenticationFilter;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OrganizationController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class OrganizationControllerSecurityTest {

    @MockitoBean
    private adliya.uz.task1.service.SessionService sessionService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private CookieProperties cookieProperties;

    @MockitoBean
    private CorsProperties corsProperties;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_CREATE")
    void createPermissionCanCreateOrganization() throws Exception {
        when(organizationService.create(any())).thenReturn(organization(false));

        mockMvc.perform(post("/api/organizations")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Ministry of Justice",
                                  "description": "Government organization"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(organizationService).create(any());
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_EDIT")
    void editPermissionCanUpdateAnyOrganization() throws Exception {
        when(organizationService.update(eq(7L), any())).thenReturn(organization(true));

        mockMvc.perform(put("/api/organizations/7")
                        .contentType("application/json")
                        .content("{\"name\":\"Updated ministry\"}"))
                .andExpect(status().isOk());

        verify(organizationService).update(eq(7L), any());
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_EDIT_OWN")
    void editOwnPermissionReachesServiceWhereOrganizationScopeIsChecked() throws Exception {
        when(organizationService.update(eq(7L), any())).thenReturn(organization(true));

        mockMvc.perform(put("/api/organizations/7")
                        .contentType("application/json")
                        .content("{\"description\":\"Updated description\"}"))
                .andExpect(status().isOk());

        verify(organizationService).update(eq(7L), any());
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_DEACTIVATE")
    void deactivatePermissionCanDeactivateOrganization() throws Exception {
        mockMvc.perform(delete("/api/organizations/7"))
                .andExpect(status().isOk());

        verify(organizationService).deactivate(7L);
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_REACTIVATE")
    void reactivatePermissionCanReactivateOrganization() throws Exception {
        when(organizationService.reactivate(7L)).thenReturn(organization(true));

        mockMvc.perform(post("/api/organizations/7/reactivate"))
                .andExpect(status().isOk());

        verify(organizationService).reactivate(7L);
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_EDIT_OWN")
    void editOwnPermissionReachesVerificationWhereScopeIsChecked() throws Exception {
        when(organizationService.verify(7L)).thenReturn(organization(true));

        mockMvc.perform(post("/api/organizations/7/verify"))
                .andExpect(status().isOk());

        verify(organizationService).verify(7L);
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_VIEW")
    void viewPermissionCannotVerify() throws Exception {
        mockMvc.perform(post("/api/organizations/7/verify"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(organizationService);
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_EDIT")
    void invalidContactDetailsAreRejectedWithFieldErrors() throws Exception {
        for (String contact : java.util.List.of(
                "{\"phone\":\"call me maybe\"}",
                "{\"latitude\":95,\"longitude\":10}",
                "{\"mapUrl\":\"javascript:alert(1)\"}",
                "{\"regionCode\":\"<script>\"}")) {
            mockMvc.perform(put("/api/organizations/7")
                            .contentType("application/json")
                            .content("{\"contact\":" + contact + "}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/organizations/7")
                        .contentType("application/json")
                        .content("{\"officialSourceUrl\":\"ftp://gov.example\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(organizationService);
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_EDIT_OWN")
    void editOwnPermissionReachesTranslationsWhereScopeIsChecked() throws Exception {
        when(organizationService.updateTranslations(eq(7L), any())).thenReturn(organization(true));

        mockMvc.perform(put("/api/organizations/7/translations")
                        .contentType("application/json")
                        .content("{\"nameTranslations\":{\"fr\":\"Ministère de la Justice\"}}"))
                .andExpect(status().isOk());

        verify(organizationService).updateTranslations(eq(7L), any());
    }

    @Test
    @WithMockUser(authorities = "ORGANIZATIONS_EDIT")
    void invalidTranslationsAreRejected() throws Exception {
        when(organizationService.updateTranslations(eq(7L), any())).thenThrow(new IllegalArgumentException("Invalid BCP 47 language tag: ??"));

        mockMvc.perform(put("/api/organizations/7/translations")
                        .contentType("application/json")
                        .content("{\"nameTranslations\":{\"??\":\"x\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "REPORTS_VIEW")
    void unrelatedPermissionCannotMutateOrganizations() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .contentType("application/json")
                        .content("{\"name\":\"Ministry of Justice\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/organizations/7")
                        .contentType("application/json")
                        .content("{\"name\":\"Updated ministry\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/organizations/7"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organizations/7/reactivate"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organizations/7/verify"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/organizations/7/translations")
                        .contentType("application/json")
                        .content("{\"nameTranslations\":{\"fr\":\"X\"}}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(organizationService);
    }

    @Test
    void anonymousUserCannotMutateOrganizations() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .contentType("application/json")
                        .content("{\"name\":\"Ministry of Justice\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/organizations/7/translations")
                        .contentType("application/json")
                        .content("{\"nameTranslations\":{\"fr\":\"X\"}}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(organizationService);
    }

    private Organization organization(boolean enabled) {
        return Organization.builder()
                .id(7L)
                .name("Ministry of Justice")
                .description("Government organization")
                .enabled(enabled)
                .build();
    }
}
