package adliya.uz.task1.controller;

import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.CorsProperties;
import adliya.uz.task1.config.security.JwtProperties;
import adliya.uz.task1.config.LoginProperties;
import adliya.uz.task1.config.MfaProperties;
import adliya.uz.task1.config.SecurityConfig;
import adliya.uz.task1.config.security.CookieUtil;
import adliya.uz.task1.config.security.JwtAuthenticationFilter;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.config.security.SessionCookies;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.service.AuthService;
import adliya.uz.task1.config.security.CustomUserDetailsService;
import adliya.uz.task1.service.LoginAttemptGuard;
import adliya.uz.task1.service.LoginRateLimiter;
import adliya.uz.task1.service.MfaService;
import adliya.uz.task1.service.RefreshTokenService;
import adliya.uz.task1.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Brute-force protection of POST /api/auth/login: per submitted account and per client address. */
@WebMvcTest(controllers = AuthController.class,
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
                "app.login.per-account-limit=3", "app.login.per-client-limit=5", "app.login.per-client-window=15m"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SessionCookies.class, CookieUtil.class, CookieProperties.class, JwtProperties.class,
        MfaProperties.class, LoginProperties.class, LoginRateLimiter.class, LoginAttemptGuard.class})
class LoginProtectionTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;
    @MockitoBean RefreshTokenService refreshTokenService;
    @MockitoBean MfaService mfaService;
    @MockitoBean SessionService sessionService;
    @MockitoBean CustomUserDetailsService userDetailsService;
    @MockitoBean CorsProperties corsProperties;
    @MockitoBean AuthenticationProvider authenticationProvider;

    private static final User ADMIN = User.builder().id(1L).firstName("Root").lastName("Admin").email("root@example.test").password("hash")
            .role(Role.builder().name("ROLE_SUPER_ADMIN").permissions(Set.of()).build()).enabled(true).organizations(Set.of()).build();

    @Test
    void repeatedWrongPasswordsLockTheAccountWithoutRevealingWhetherItExists() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json").header("X-Real-IP", "198.51.100." + i)
                            .content("{\"email\":\"ghost@example.test\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType("application/json").header("X-Real-IP", "198.51.100.9")
                        .content("{\"email\":\"GHOST@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many attempts; try again later"));
        verify(authService, times(3)).login(any());
        // Other accounts are unaffected.
        mvc.perform(post("/api/auth/login").contentType("application/json").header("X-Real-IP", "198.51.100.9")
                        .content("{\"email\":\"someone@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oneClientAddressCannotTryMoreThanTheConfiguredNumberOfPasswords() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json").header("X-Real-IP", "203.0.113.7")
                            .content("{\"email\":\"user" + i + "@example.test\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType("application/json").header("X-Real-IP", "203.0.113.7")
                        .content("{\"email\":\"user9@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests());
        verify(authService, times(5)).login(any());
    }

    @Test
    void successfulLoginClearsTheAccountCounter() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid credentials")).thenThrow(new BadCredentialsException("Invalid credentials"))
                .thenReturn(ADMIN).thenThrow(new BadCredentialsException("Invalid credentials"));
        when(mfaService.decide(ADMIN)).thenReturn(MfaService.Decision.FULL_SESSION);
        when(jwtService.generateToken(any(), any())).thenReturn("access");
        when(refreshTokenService.createSession(any(), any())).thenReturn(new RefreshTokenService.IssuedSession("refresh", "sid"));
        String body = "{\"email\":\"root@example.test\",\"password\":\"x\"}";
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        verify(mfaService, never()).createChallenge(any(), any());
    }
}
