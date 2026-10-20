package adliya.uz.task1.controller;

import adliya.uz.task1.config.CorsProperties;
import adliya.uz.task1.config.SecurityConfig;
import adliya.uz.task1.config.security.CookieProperties;
import adliya.uz.task1.config.security.CustomUserDetailsService;
import adliya.uz.task1.config.security.JwtAuthenticationFilter;
import adliya.uz.task1.config.security.JwtService;
import adliya.uz.task1.dto.AddLanguageRequest;
import adliya.uz.task1.entity.Language;
import adliya.uz.task1.service.LanguageService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LanguageController.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class LanguageControllerSecurityTest {

    @MockitoBean
    private adliya.uz.task1.service.SessionService sessionService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LanguageService languageService;

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
    void activeLanguagesAndCatalogRemainPublic() throws Exception {
        when(languageService.getActive()).thenReturn(List.of());
        when(languageService.getCatalog()).thenReturn(List.of());

        mockMvc.perform(get("/api/languages"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/languages/catalog"))
                .andExpect(status().isOk());

        verify(languageService).getActive();
        verify(languageService).getCatalog();
    }

    @Test
    @WithMockUser(authorities = "LANGUAGES_VIEW")
    void viewPermissionCanSearchLanguages() throws Exception {
        when(languageService.search("uz")).thenReturn(List.of());

        mockMvc.perform(get("/api/languages/search").param("q", "uz"))
                .andExpect(status().isOk());

        verify(languageService).search("uz");
    }

    @Test
    @WithMockUser(authorities = "LANGUAGES_CREATE")
    void createPermissionCanAddLanguage() throws Exception {
        when(languageService.add(any(AddLanguageRequest.class))).thenReturn(language());

        mockMvc.perform(post("/api/languages")
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "uz-Cyrl",
                                  "nativeName": "Ўзбекча"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(languageService).add(any(AddLanguageRequest.class));
    }

    @Test
    @WithMockUser(authorities = "LANGUAGES_DELETE")
    void deletePermissionCanRemoveLanguage() throws Exception {
        mockMvc.perform(delete("/api/languages/7"))
                .andExpect(status().isNoContent());

        verify(languageService).remove(7L);
    }

    @Test
    @WithMockUser(authorities = "REPORTS_VIEW")
    void unrelatedPermissionCannotManageLanguages() throws Exception {
        mockMvc.perform(get("/api/languages/search").param("q", "uz"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/languages")
                        .contentType("application/json")
                        .content("{\"code\":\"uz-Cyrl\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/languages/7"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(languageService);
    }

    @Test
    void anonymousUserCannotManageLanguages() throws Exception {
        mockMvc.perform(get("/api/languages/search").param("q", "uz"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(languageService);
    }

    private Language language() {
        return Language.builder()
                .id(7L)
                .code("uz-Cyrl")
                .nameNative("Ўзбекча")
                .isDefault(false)
                .active(true)
                .build();
    }
}
