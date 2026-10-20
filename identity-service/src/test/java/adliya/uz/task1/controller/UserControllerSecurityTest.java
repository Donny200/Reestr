package adliya.uz.task1.controller;

import adliya.uz.task1.config.CorsProperties;
import adliya.uz.task1.config.SecurityConfig;
import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.security.CustomUserDetailsService;
import adliya.uz.task1.config.security.JwtAuthenticationFilter;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.dto.PasswordResetLinkResponse;
import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.service.PasswordResetService;
import adliya.uz.task1.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        controllers = UserController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerSecurityTest {

    @MockitoBean
    private adliya.uz.task1.service.SessionService sessionService;
    @MockitoBean
    private adliya.uz.task1.service.MfaService mfaService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PasswordResetService passwordResetService;

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
    @WithMockUser(username = "orgadmin@example.com", authorities = {"ROLE_ORG_ADMIN", "USERS_VIEW", "USERS_EDIT"})
    void mfaResetAndSessionAdministrationRequireSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/user/7/mfa")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/user/7/mfa/reset")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/user/7/sessions")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/user/7/sessions/s-1")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/user/7/sessions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/user/7/security-events")).andExpect(status().isForbidden());
        verifyNoInteractions(mfaService, sessionService);
    }

    @Test
    @WithMockUser(username = "root@example.com", authorities = {"ROLE_SUPER_ADMIN"})
    void superAdminManagesSessionsAndSecondFactorsOfOtherUsersWithoutSeeingSecrets() throws Exception {
        when(userService.getById(7L)).thenReturn(adliya.uz.task1.entity.User.builder().id(7L).build());
        when(mfaService.status(any())).thenReturn(new adliya.uz.task1.dto.MfaStatusResponse(true, true, true, null, null, false, 8));
        mockMvc.perform(get("/api/user/7/mfa")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(8))
                .andExpect(jsonPath("$.secret").doesNotExist());
        mockMvc.perform(post("/api/user/7/mfa/reset")).andExpect(status().isNoContent());
        verify(mfaService).adminReset(any(), eq(7L));
        mockMvc.perform(delete("/api/user/7/sessions")).andExpect(status().isNoContent());
        verify(sessionService).revokeAllForAdmin(7L);
    }

    @Test
    @WithMockUser(username = "viewer@example.com", authorities = "USERS_VIEW")
    void usersViewPermissionCanReadButCannotMutate() throws Exception {
        when(userService.getAllForLegacyApi()).thenReturn(List.of());
        when(userService.getForLegacyApi(7L)).thenReturn(response());

        mockMvc.perform(get("/api/user"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/user/7"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content(validCreateRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/user/7")
                        .contentType("application/json")
                        .content("{\"firstName\":\"Updated\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/user/7"))
                .andExpect(status().isForbidden());

        verify(userService).getAllForLegacyApi();
        verify(userService).getForLegacyApi(7L);
    }

    @Test
    @WithMockUser(username = "creator@example.com", authorities = "USERS_CREATE")
    void usersCreatePermissionCanCreateButCannotRead() throws Exception {
        when(userService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content(validCreateRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/user"))
                .andExpect(status().isForbidden());

        verify(userService).create(any());
    }

    @Test
    @WithMockUser(username = "editor@example.com", authorities = {"USERS_VIEW", "USERS_EDIT", "USERS_CREATE", "USERS_DEACTIVATE", "ROLE_ORG_ADMIN"})
    void onlySuperAdminCanInitiateAPasswordResetEvenWithEveryUserPermission() throws Exception {
        mockMvc.perform(post("/api/user/7/password-reset"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    @WithMockUser(username = "root@example.com", authorities = "ROLE_SUPER_ADMIN")
    void superAdminReceivesTheResetLinkOnceAndNeverAPassword() throws Exception {
        when(passwordResetService.initiateByAdmin(7L)).thenReturn(new PasswordResetLinkResponse(7L,
                "/reset-password?token=raw-token-value", java.time.Instant.parse("2026-10-12T10:00:00Z")));

        var result = mockMvc.perform(post("/api/user/7/password-reset"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.resetPath").value("/reset-password?token=raw-token-value"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-12T10:00:00Z"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString().toLowerCase())
                .doesNotContain("password\"", "hash");
        verify(passwordResetService).initiateByAdmin(7L);
    }

    @Test
    @WithMockUser(username = "editor@example.com", authorities = "USERS_EDIT")
    void usersEditPermissionCanUpdateButCannotDeactivate() throws Exception {
        when(userService.update(eq(7L), any())).thenReturn(response());

        mockMvc.perform(put("/api/user/7")
                        .contentType("application/json")
                        .content("{\"firstName\":\"Updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/user/7"))
                .andExpect(status().isForbidden());

        verify(userService).update(eq(7L), any());
    }

    @Test
    void anonymousUserCannotAccessLegacyUserApi() throws Exception {
        mockMvc.perform(get("/api/user"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "root@example.com", authorities = {
            "ROLE_SUPER_ADMIN", "USERS_VIEW", "USERS_CREATE", "USERS_EDIT"
    })
    void passwordNeverAppearsInAnyLegacyUserResponse() throws Exception {
        UserResponse response = response();
        when(userService.getAllForLegacyApi()).thenReturn(List.of(response));
        when(userService.getForLegacyApi(7L)).thenReturn(response);
        when(userService.create(any())).thenReturn(response);
        when(userService.update(eq(7L), any())).thenReturn(response);

        mockMvc.perform(get("/api/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..password").doesNotExist());
        mockMvc.perform(get("/api/user/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..password").doesNotExist());
        mockMvc.perform(post("/api/user")
                        .contentType("application/json")
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$..password").doesNotExist());
        mockMvc.perform(put("/api/user/7")
                        .contentType("application/json")
                        .content("{\"firstName\":\"Updated\",\"password\":\"NewPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..password").doesNotExist());
    }

    @Test
    @WithMockUser(username = "manager@example.com", authorities = "USERS_DEACTIVATE")
    void superAdminDeleteSoftDeactivatesAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/user/7"))
                .andExpect(status().isNoContent());

        verify(userService).deactivate(7L);
    }

    private String validCreateRequest() {
        return """
                {
                  "firstName": "Alice",
                  "lastName": "Admin",
                  "email": "alice@example.com",
                  "password": "StrongPassword123",
                  "phone": "+998901234567",
                  "roleId": 3,
                  "organizationIds": [10]
                }
                """;
    }

    private UserResponse response() {
        return UserResponse.builder()
                .id(7L)
                .firstName("Alice")
                .lastName("Admin")
                .email("alice@example.com")
                .phone("+998901234567")
                .role("ROLE_ORG_ADMIN")
                .enabled(true)
                .createdAt(LocalDateTime.of(2026, 8, 25, 10, 0))
                .organizationIds(Set.of(10L))
                .build();
    }
}
