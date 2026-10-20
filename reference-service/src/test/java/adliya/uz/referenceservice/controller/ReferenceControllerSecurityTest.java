package adliya.uz.referenceservice.controller;

import adliya.uz.referenceservice.security.JwtAuthenticationFilter;
import adliya.uz.referenceservice.security.SecurityConfig;
import adliya.uz.referenceservice.security.SimpleJwtService;
import adliya.uz.referenceservice.service.InterfaceTranslationService;
import adliya.uz.referenceservice.service.TranslationKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {TranslationKeyController.class, InterfaceTranslationController.class},
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReferenceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranslationKeyService translationKeyService;

    @MockitoBean
    private InterfaceTranslationService interfaceTranslationService;

    @MockitoBean
    private SimpleJwtService simpleJwtService;

    @Test
    void baseTranslationDictionaryRemainsPublic() throws Exception {
        when(interfaceTranslationService.getTranslations("uz")).thenReturn(Map.of());

        mockMvc.perform(get("/api/interface-translations/uz"))
                .andExpect(status().isOk());

        verify(interfaceTranslationService).getTranslations("uz");
    }

    @Test
    @WithMockUser(authorities = "TRANSLATION_KEYS_VIEW")
    void translationKeyViewPermissionCanOnlyReadKeys() throws Exception {
        when(translationKeyService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/translation-keys"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/translation-keys")
                        .contentType("application/json")
                        .content(validCreateKeyRequest()))
                .andExpect(status().isForbidden());

        verify(translationKeyService).getAll();
    }

    @Test
    @WithMockUser(authorities = "TRANSLATION_KEYS_CREATE")
    void translationKeyCreatePermissionCanCreateButCannotEdit() throws Exception {
        mockMvc.perform(post("/api/translation-keys")
                        .contentType("application/json")
                        .content(validCreateKeyRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/translation-keys/7")
                        .contentType("application/json")
                        .content(validUpdateKeyRequest()))
                .andExpect(status().isForbidden());

        verify(translationKeyService).create(any());
    }

    @Test
    @WithMockUser(authorities = "TRANSLATION_KEYS_EDIT")
    void translationKeyEditPermissionCanEditButCannotDeactivate() throws Exception {
        mockMvc.perform(put("/api/translation-keys/7")
                        .contentType("application/json")
                        .content(validUpdateKeyRequest()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/translation-keys/7"))
                .andExpect(status().isForbidden());

        verify(translationKeyService).update(eq(7L), any());
    }

    @Test
    @WithMockUser(authorities = "TRANSLATION_KEYS_DEACTIVATE")
    void translationKeyDeactivatePermissionCanDeactivateButCannotRead() throws Exception {
        mockMvc.perform(delete("/api/translation-keys/7"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/translation-keys"))
                .andExpect(status().isForbidden());

        verify(translationKeyService).deactivate(7L);
    }

    @Test
    @WithMockUser(authorities = "TRANSLATIONS_EDIT")
    void translationEditPermissionCanMutateButCannotViewCoverage() throws Exception {
        when(interfaceTranslationService.getTranslations("uz"))
                .thenReturn(Map.of("home.title", "Bosh sahifa"));

        mockMvc.perform(put("/api/interface-translations/uz")
                        .contentType("application/json")
                        .content(validTranslationRequest()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/interface-translations/uz/home.title"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/interface-translations/uz/coverage"))
                .andExpect(status().isForbidden());

        verify(interfaceTranslationService).updateTranslations(eq("uz"), any());
        verify(interfaceTranslationService).deleteTranslation("uz", "home.title");
    }

    @Test
    @WithMockUser(authorities = "TRANSLATIONS_VIEW_COVERAGE")
    void translationCoveragePermissionCanInspectCoverageButCannotMutate() throws Exception {
        when(interfaceTranslationService.getExactTranslations("uz")).thenReturn(Map.of());
        when(interfaceTranslationService.getMissingKeys("uz")).thenReturn(List.of());

        mockMvc.perform(get("/api/interface-translations/uz/exact"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/interface-translations/uz/missing"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/interface-translations/uz/coverage"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/interface-translations/uz")
                        .contentType("application/json")
                        .content(validTranslationRequest()))
                .andExpect(status().isForbidden());

        verify(interfaceTranslationService).getExactTranslations("uz");
        verify(interfaceTranslationService).getMissingKeys("uz");
    }

    @Test
    @WithMockUser(authorities = "UNRELATED_PERMISSION")
    void unrelatedPermissionCannotAccessProtectedReferenceEndpoints() throws Exception {
        mockMvc.perform(get("/api/translation-keys"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/interface-translations/uz/coverage"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(translationKeyService);
    }

    private String validCreateKeyRequest() {
        return """
                {
                  "translationKey": "home.title",
                  "description": "Home page title",
                  "required": true,
                  "defaultValue": "Home"
                }
                """;
    }

    private String validUpdateKeyRequest() {
        return """
                {
                  "description": "Updated title",
                  "required": true,
                  "active": true
                }
                """;
    }

    private String validTranslationRequest() {
        return """
                {
                  "translations": {
                    "home.title": "Bosh sahifa"
                  }
                }
                """;
    }
}
