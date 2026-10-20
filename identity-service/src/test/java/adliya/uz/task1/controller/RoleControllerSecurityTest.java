package adliya.uz.task1.controller;

import adliya.uz.task1.config.CorsProperties;
import adliya.uz.task1.config.SecurityConfig;
import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.security.CustomUserDetailsService;
import adliya.uz.task1.config.security.JwtAuthenticationFilter;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.dto.RoleResponse;
import adliya.uz.task1.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RoleController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RoleControllerSecurityTest {

    @MockitoBean
    private adliya.uz.task1.service.SessionService sessionService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

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
    @WithMockUser(username = "org-admin@example.com", roles = "ORG_ADMIN")
    void ordinaryAdministratorCannotAssignRole() throws Exception {
        mockMvc.perform(post("/api/roles/assign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 42,
                                  "roleId": 3
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roleService);
    }

    @Test
    @WithMockUser(username = "ordinary@example.com", roles = "USER")
    void ordinaryUserCannotUseAssignEndpointToPromoteSelf() throws Exception {
        mockMvc.perform(post("/api/roles/assign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 42,
                                  "roleId": 1
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roleService);
    }

    @Test
    @WithMockUser(authorities = "ROLES_VIEW")
    void rolesViewPermissionCanReadRoles() throws Exception {
        when(roleService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk());

        verify(roleService).getAll();
    }

    @Test
    @WithMockUser(authorities = "ROLES_MANAGE_PERMISSIONS")
    void managePermissionsAuthorityCanUpdateRolePermissions() throws Exception {
        when(roleService.assignPermissions(eq(7L), any())).thenReturn(
                RoleResponse.builder().id(7L).name("ROLE_EDITOR").permissions(List.of()).build()
        );

        mockMvc.perform(put("/api/roles/7/permissions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "permissionIds": []
                                }
                                """))
                .andExpect(status().isOk());

        verify(roleService).assignPermissions(eq(7L), any());
    }

    @Test
    @WithMockUser(username = "root@example.com", roles = "SUPER_ADMIN")
    void superAdminCanAssignRoleUsingDtoBody() throws Exception {
        when(roleService.assignRoleToUser(any(), eq("root@example.com")))
                .thenReturn("Role assigned");

        mockMvc.perform(post("/api/roles/assign")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 42,
                                  "roleId": 3
                                }
                                """))
                .andExpect(status().isOk());

        verify(roleService).assignRoleToUser(any(), eq("root@example.com"));
    }

    @Test
    void anonymousUserCannotReadRoles() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roleService);
    }
}
