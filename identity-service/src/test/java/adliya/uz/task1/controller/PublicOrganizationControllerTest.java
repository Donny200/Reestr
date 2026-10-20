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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PublicOrganizationController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PublicOrganizationControllerTest {

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
    void anonymousVisitorSeesContactAndVerificationButNotTheVerifyingStaffMember() throws Exception {
        Organization organization = Organization.builder()
                .id(7L)
                .name("Ministry of Justice")
                .enabled(true)
                .address("Tashkent, Sayilgoh 5")
                .phone("+998 71 200-00-00")
                .latitude(41.31)
                .longitude(69.27)
                .officialSourceUrl("https://gov.example/justice")
                .lastVerifiedAt(Instant.now())
                .verifiedByUserId(42L)
                .build();
        when(organizationService.getPublicById(7L)).thenReturn(organization);

        mockMvc.perform(get("/api/public/organizations/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.address").value("Tashkent, Sayilgoh 5"))
                .andExpect(jsonPath("$.contact.latitude").value(41.31))
                .andExpect(jsonPath("$.contact.workingHours").doesNotExist())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.officialSourceUrl").value("https://gov.example/justice"))
                .andExpect(jsonPath("$.verifiedByUserId").doesNotExist());
    }
}
