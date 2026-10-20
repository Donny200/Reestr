package adliya.uz.task1.controller;

import adliya.uz.task1.config.MfaProperties;
import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.security.CookieUtil;
import adliya.uz.task1.config.security.SessionCookies;
import adliya.uz.task1.service.MfaService;
import adliya.uz.task1.service.SessionService;
import adliya.uz.task1.config.security.CustomUserPrincipal;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.config.security.MandatoryPasswordChangeFilter;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.InvalidRefreshTokenException;
import adliya.uz.task1.service.AuthService;
import adliya.uz.task1.service.RefreshTokenService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerMandatoryPasswordChangeTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private MfaService mfaService;

    @Mock
    private SessionService sessionService;

    @Test
    void flaggedUserIsDeniedEveryOtherEndpoint() throws Exception {
        flaggedMockMvc().perform(get("/api/private-resource"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Password must be changed before accessing this resource"));
    }

    @Test
    void flaggedUserCanChangePasswordReadProfileAndLogout() throws Exception {
        User changedUser = user(false);
        when(authService.changePassword(any())).thenReturn(changedUser);
        when(authService.getCurrentUserEntity()).thenReturn(user(true));
        when(jwtService.generateToken(changedUser, "session-1")).thenReturn("new-access-value");
        when(refreshTokenService.createSession(eq(changedUser), any()))
                .thenReturn(new RefreshTokenService.IssuedSession("new-refresh-value", "session-1"));
        when(cookieUtil.createAccessCookie("new-access-value"))
                .thenReturn(sessionCookie("accessToken", "new-access-value", "/"));
        when(cookieUtil.createRefreshCookie("new-refresh-value"))
                .thenReturn(sessionCookie("refreshToken", "new-refresh-value", "/api/auth"));
        when(cookieUtil.createLogoutAccessCookie()).thenReturn(logoutCookie("accessToken", "/"));
        when(cookieUtil.createLogoutRefreshCookie()).thenReturn(logoutCookie("refreshToken", "/api/auth"));
        MockMvc mockMvc = flaggedMockMvc();

        MvcResult passwordChange = mockMvc.perform(post("/api/auth/change-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "currentPassword": "TemporaryPassword123",
                                  "newPassword": "PermanentPassword456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(passwordChange.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("accessToken=new-access-value"))
                .anyMatch(cookie -> cookie.startsWith("refreshToken=new-refresh-value"));
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());

        verify(authService).changePassword(any());
    }

    @Test
    void flaggedLoginIssuesAccessCookieAndClearsRatherThanIssuesRefreshToken() throws Exception {
        User user = user(true);
        when(authService.login(any())).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("access-value");
        when(cookieUtil.createAccessCookie("access-value"))
                .thenReturn(sessionCookie("accessToken", "access-value", "/"));
        when(cookieUtil.createLogoutRefreshCookie())
                .thenReturn(logoutCookie("refreshToken", "/api/auth"));

        MvcResult result = baseMockMvc().perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginRequest()))
                .andExpect(status().isOk())
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("accessToken=access-value"));
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("refreshToken=")
                && cookie.contains("Max-Age=0"));
        assertThat(cookies).noneMatch(cookie -> cookie.startsWith("refreshToken=")
                && !cookie.contains("Max-Age=0"));
        verify(refreshTokenService, never()).createRefreshToken(any());
        verify(refreshTokenService, never()).createSession(any(), any());
    }

    @Test
    void refreshWithoutAccessCookieIsRejectedWhenStoredSessionRequiresPasswordChange() throws Exception {
        when(refreshTokenService.rotate("old-refresh"))
                .thenThrow(new InvalidRefreshTokenException("Password change is required"));
        when(cookieUtil.createLogoutAccessCookie()).thenReturn(logoutCookie("accessToken", "/"));
        when(cookieUtil.createLogoutRefreshCookie()).thenReturn(logoutCookie("refreshToken", "/api/auth"));

        assertThat(controller().refresh("old-refresh").getStatusCode().value()).isEqualTo(401);

        verify(refreshTokenService).rotate("old-refresh");
    }

    @Test
    void ordinaryLoginStillIssuesAccessAndRefreshCookies() throws Exception {
        User user = user(false);
        when(authService.login(any())).thenReturn(user);
        when(jwtService.generateToken(user, "session-2")).thenReturn("access-value");
        when(refreshTokenService.createSession(eq(user), any()))
                .thenReturn(new RefreshTokenService.IssuedSession("refresh-value", "session-2"));
        when(cookieUtil.createAccessCookie("access-value"))
                .thenReturn(sessionCookie("accessToken", "access-value", "/"));
        when(cookieUtil.createRefreshCookie("refresh-value"))
                .thenReturn(sessionCookie("refreshToken", "refresh-value", "/api/auth"));

        MvcResult result = baseMockMvc().perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginRequest()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("accessToken=access-value"))
                .anyMatch(cookie -> cookie.startsWith("refreshToken=refresh-value"));
    }

    @Test
    void ordinaryAuthenticatedUserIsNotRestrictedByPasswordChangeGate() throws Exception {
        mockMvcWithPrincipal(false).perform(get("/api/private-resource"))
                .andExpect(status().isNotFound());
    }

    private MockMvc baseMockMvc() {
        return MockMvcBuilders.standaloneSetup(controller())
                .addPlaceholderValue("app.cookie.refresh-token-name", "refreshToken")
                .build();
    }

    private MockMvc flaggedMockMvc() {
        return mockMvcWithPrincipal(true);
    }

    private MockMvc mockMvcWithPrincipal(boolean mustChangePassword) {
        Filter authenticationFilter = (request, response, chain) -> {
            CustomUserPrincipal principal = new CustomUserPrincipal(
                    "root@example.com",
                    "hash",
                    List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")),
                    true,
                    mustChangePassword
            );
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    )
            );
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        return MockMvcBuilders.standaloneSetup(controller())
                .addPlaceholderValue("app.cookie.refresh-token-name", "refreshToken")
                .addFilters(authenticationFilter, new MandatoryPasswordChangeFilter())
                .build();
    }

    private AuthController controller() {
        org.mockito.Mockito.lenient().when(mfaService.decide(any())).thenReturn(MfaService.Decision.FULL_SESSION);
        SessionCookies sessionCookies = new SessionCookies(cookieUtil, jwtService, refreshTokenService, new CookieProperties());
        adliya.uz.task1.config.LoginProperties loginProperties = new adliya.uz.task1.config.LoginProperties();
        return new AuthController(authService, sessionCookies, refreshTokenService, mfaService, sessionService, new MfaProperties(),
                loginProperties, new adliya.uz.task1.service.LoginRateLimiter(loginProperties),
                new adliya.uz.task1.service.LoginAttemptGuard(loginProperties));
    }

    private User user(boolean mustChangePassword) {
        return User.builder()
                .id(1L)
                .firstName("Root")
                .lastName("Admin")
                .email("root@example.com")
                .password("hash")
                .role(Role.builder().name("ROLE_SUPER_ADMIN").build())
                .enabled(true)
                .mustChangePassword(mustChangePassword)
                .organizations(Set.of())
                .build();
    }

    private ResponseCookie sessionCookie(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .path(path)
                .build();
    }

    private ResponseCookie logoutCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .path(path)
                .maxAge(0)
                .build();
    }

    private String loginRequest() {
        return """
                {
                  "email": "root@example.com",
                  "password": "TemporaryPassword123"
                }
                """;
    }
}
