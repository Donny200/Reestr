package adliya.uz.task1.controller;

import adliya.uz.task1.config.CorsProperties;
import adliya.uz.task1.config.MfaProperties;
import adliya.uz.task1.config.SecurityConfig;
import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.security.CookieUtil;
import adliya.uz.task1.config.security.CustomUserDetailsService;
import adliya.uz.task1.config.security.JwtAuthenticationFilter;
import adliya.uz.task1.config.security.JwtProperties;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.config.security.SessionCookies;
import adliya.uz.task1.entity.MfaChallenge;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.MfaRequiredException;
import adliya.uz.task1.service.AuthService;
import adliya.uz.task1.service.MfaService;
import adliya.uz.task1.service.RefreshTokenService;
import adliya.uz.task1.service.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves that no access or refresh token exists before the second factor and that the challenge cookie reaches only the MFA endpoints. */
@WebMvcTest(controllers = {AuthController.class, MfaController.class},
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "jwt.expiration=60000", "jwt.refresh-expiration=120000"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SessionCookies.class, CookieUtil.class, CookieProperties.class, JwtProperties.class, MfaProperties.class,
        adliya.uz.task1.config.LoginProperties.class, adliya.uz.task1.service.LoginRateLimiter.class, adliya.uz.task1.service.LoginAttemptGuard.class})
class MfaLoginFlowTest {

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
    void passwordAloneYieldsOnlyAChallengeCookieScopedToTheMfaEndpoints() throws Exception {
        when(authService.login(any())).thenReturn(ADMIN);
        when(mfaService.decide(ADMIN)).thenReturn(MfaService.Decision.VERIFY);
        when(mfaService.createChallenge(eq(ADMIN), eq(MfaChallenge.Purpose.VERIFY)))
                .thenReturn(new MfaService.IssuedChallenge("raw-challenge", Instant.parse("2026-10-14T09:05:00Z")));

        MvcResult result = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"root@example.test\",\"password\":\"Password123!\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.enrollmentRequired").value(false))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("mfaChallenge=raw-challenge") && cookie.contains("Path=/api/auth/mfa") && cookie.contains("HttpOnly"));
        assertThat(cookies).noneMatch(cookie -> cookie.startsWith("accessToken=") && !cookie.contains("Max-Age=0"));
        assertThat(cookies).noneMatch(cookie -> cookie.startsWith("refreshToken=") && !cookie.contains("Max-Age=0"));
        verify(refreshTokenService, never()).createSession(any(), any());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void enrollmentRequiredUsersGetAnEnrollChallenge() throws Exception {
        when(authService.login(any())).thenReturn(ADMIN);
        when(mfaService.decide(ADMIN)).thenReturn(MfaService.Decision.ENROLL);
        when(mfaService.createChallenge(eq(ADMIN), eq(MfaChallenge.Purpose.ENROLL)))
                .thenReturn(new MfaService.IssuedChallenge("enroll-challenge", Instant.parse("2026-10-14T09:05:00Z")));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"root@example.test\",\"password\":\"Password123!\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.enrollmentRequired").value(true));
        verify(refreshTokenService, never()).createSession(any(), any());
    }

    @Test
    void sessionTokensAreIssuedOnlyAfterTheCodeIsVerified() throws Exception {
        when(mfaService.verify(eq("raw-challenge"), eq("123456"), any())).thenReturn(ADMIN);
        when(refreshTokenService.createSession(eq(ADMIN), any())).thenReturn(new RefreshTokenService.IssuedSession("refresh-raw", "session-1"));
        when(jwtService.generateToken(ADMIN, "session-1")).thenReturn("access-jwt");

        MvcResult result = mvc.perform(post("/api/auth/mfa/verify").cookie(new Cookie("mfaChallenge", "raw-challenge"))
                        .contentType("application/json").content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("root@example.test"))
                .andReturn();
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("accessToken=access-jwt"));
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("refreshToken=refresh-raw"));
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("mfaChallenge=;") && cookie.contains("Max-Age=0"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refresh-raw").doesNotContain("raw-challenge");
    }

    @Test
    void wrongOrMissingChallengeNeverStartsASession() throws Exception {
        when(mfaService.verify(eq("raw-challenge"), eq("000000"), any())).thenThrow(new MfaRequiredException("Invalid verification code"));
        mvc.perform(post("/api/auth/mfa/verify").cookie(new Cookie("mfaChallenge", "raw-challenge"))
                        .contentType("application/json").content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/verify").contentType("application/json").content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
        verify(refreshTokenService, never()).createSession(any(), any());
    }

    @Test
    void theChallengeCookieGrantsNothingOutsideTheMfaEndpoints() throws Exception {
        mvc.perform(get("/api/auth/sessions").cookie(new Cookie("mfaChallenge", "raw-challenge"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/mfa").cookie(new Cookie("mfaChallenge", "raw-challenge"))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/auth/sessions/s-1").cookie(new Cookie("mfaChallenge", "raw-challenge"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(sessionService);
    }
}
