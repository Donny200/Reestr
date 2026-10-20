package adliya.uz.task1.controller;

import adliya.uz.task1.config.CorsProperties;
import adliya.uz.task1.config.PasswordResetProperties;
import adliya.uz.task1.config.SecurityConfig;
import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.security.CustomUserDetailsService;
import adliya.uz.task1.config.security.JwtAuthenticationFilter;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.InvalidPasswordResetTokenException;
import adliya.uz.task1.exception.PasswordResetUnavailableException;
import adliya.uz.task1.exception.RateLimitExceededException;
import adliya.uz.task1.service.PasswordResetRateLimiter;
import adliya.uz.task1.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PasswordResetController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, PasswordResetProperties.class})
class PasswordResetControllerSecurityTest {

    @MockitoBean
    private adliya.uz.task1.service.SessionService sessionService;

    private static final String SECRET_TOKEN = "super-secret-raw-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private PasswordResetRateLimiter rateLimiter;

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
    void anonymousVisitorsCanReadOptionsAndRequestAResetWithAnIdenticalAnswerEitherWay() throws Exception {
        when(passwordResetService.selfServiceAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/auth/password-reset/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfServiceAvailable").value(true));

        for (String email : new String[]{"known@example.test", "unknown@example.test"}) {
            MvcResult result = mockMvc.perform(post("/api/auth/password-reset/request")
                            .header("X-Real-IP", "203.0.113.9")
                            .contentType("application/json")
                            .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.accepted").value(true))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"accepted\":true}");
        }
        verify(passwordResetService).requestByEmail("known@example.test");
        verify(passwordResetService).requestByEmail("unknown@example.test");
        verify(rateLimiter, org.mockito.Mockito.times(2)).acquire("203.0.113.9");
    }

    @Test
    void completionIsAnonymousValidatedAndNeverEchoesTheToken() throws Exception {
        when(passwordResetService.complete(SECRET_TOKEN, "New-password-2026")).thenReturn(User.builder().id(7L).build());

        MvcResult ok = mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType("application/json")
                        .content("{\"token\":\"" + SECRET_TOKEN + "\",\"newPassword\":\"New-password-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset").value(true))
                .andReturn();
        assertThat(ok.getResponse().getContentAsString()).doesNotContain(SECRET_TOKEN, "New-password-2026", "7");

        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType("application/json")
                        .content("{\"token\":\"" + SECRET_TOKEN + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        verify(passwordResetService, never()).complete(anyString(), eq("short"));
    }

    @Test
    void invalidTokensGetAGenericBadRequestWithoutLeakingDetails() throws Exception {
        when(passwordResetService.complete(eq("bad-token"), anyString()))
                .thenThrow(new InvalidPasswordResetTokenException("Reset token belongs to a disabled account"));

        MvcResult result = mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType("application/json")
                        .content("{\"token\":\"bad-token\",\"newPassword\":\"New-password-2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This password reset link is invalid or has expired"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("bad-token", "disabled");
    }

    @Test
    void rateLimitedAttemptsReturn429WithRetryAfterBeforeTouchingTheService() throws Exception {
        doThrow(new RateLimitExceededException(Duration.ofMinutes(5))).when(rateLimiter).acquire(any());

        mockMvc.perform(post("/api/auth/password-reset/request").contentType("application/json")
                        .content("{\"email\":\"known@example.test\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"));
        mockMvc.perform(post("/api/auth/password-reset/complete").contentType("application/json")
                        .content("{\"token\":\"x\",\"newPassword\":\"New-password-2026\"}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void missingMailDeliveryIsReportedAsUnavailableNotAsAnInsecureFallback() throws Exception {
        doThrow(new PasswordResetUnavailableException("Self-service password reset is not configured; ask an administrator"))
                .when(passwordResetService).requestByEmail(anyString());

        mockMvc.perform(post("/api/auth/password-reset/request").contentType("application/json")
                        .content("{\"email\":\"known@example.test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not configured")));
    }
}
