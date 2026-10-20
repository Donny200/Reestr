package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.config.SecurityConfig;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.security.*;
import adliya.uz.functioncatalogservice.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {FunctionWorkflowController.class, FunctionCategoryController.class, FunctionImportController.class},
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class EditorialEndpointSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrgFunctionService functions;
    @MockitoBean FunctionCategoryService categories;
    @MockitoBean FunctionImportService importer;
    @MockitoBean SimpleJwtService jwt;

    @ParameterizedTest
    @CsvSource({
        "POST,/api/functions/1/submit-for-review,FUNCTIONS_SUBMIT_REVIEW",
        "POST,/api/functions/1/withdraw,FUNCTIONS_SUBMIT_REVIEW",
        "GET,/api/functions/pending-review,FUNCTIONS_REVIEW",
        "POST,/api/functions/1/reject,FUNCTIONS_REVIEW",
        "POST,/api/functions/1/publish,FUNCTIONS_PUBLISH",
        "POST,/api/functions/1/reactivate,FUNCTIONS_REACTIVATE",
        "GET,/api/functions/1/audit,AUDIT_VIEW",
        "PUT,/api/functions/1/translations,FUNCTIONS_EDIT+FUNCTIONS_TRANSLATIONS_EDIT",
        "POST,/api/functions/categories,FUNCTION_CATEGORIES_MANAGE",
        "PUT,/api/functions/categories/1,FUNCTION_CATEGORIES_MANAGE",
        "DELETE,/api/functions/categories/1,FUNCTION_CATEGORIES_MANAGE",
        "FILE,/api/functions/import,FUNCTIONS_IMPORT"
    })
    void everyEndpointRequiresPermissionNotJustRole(String method, String path, String permissions) throws Exception {
        mvc.perform(request(method,path)).andExpect(status().is4xxClientError());
        mvc.perform(request(method,path).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(functions,categories,importer);

        var function = OrgFunction.builder().id(1L).name("Draft").build();
        lenient().when(functions.submitForReview(1L)).thenReturn(function);
        lenient().when(functions.withdraw(1L)).thenReturn(function);
        lenient().when(functions.reject(eq(1L),anyString())).thenReturn(function);
        lenient().when(functions.publish(1L)).thenReturn(function);
        lenient().when(functions.reactivate(1L)).thenReturn(function);
        lenient().when(functions.updateTranslations(eq(1L),any())).thenReturn(function);
        mvc.perform(request(method,path).with(user("editor").authorities(Arrays.stream(permissions.split("\\+"))
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList())))
                .andExpect(status().is2xxSuccessful());
    }
    @Test void translationPermissionAloneCannotEdit() throws Exception {
        mvc.perform(request("PUT","/api/functions/1/translations").with(user("translator").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("FUNCTIONS_TRANSLATIONS_EDIT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(functions);
    }
    @Test void rejectRequiresNonblankReason() throws Exception {
        mvc.perform(post("/api/functions/1/reject").contentType("application/json").content("{\"reason\":\" \"}")
                .with(user("reviewer").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FUNCTIONS_REVIEW"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(functions);
    }
    @Test void categoryReadsRemainPublic() throws Exception {
        mvc.perform(get("/api/functions/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/functions/categories/1")).andExpect(status().isOk());
    }
    private MockHttpServletRequestBuilder request(String method, String path) {
        var builder = switch(method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            case "FILE" -> multipart(path).file(new MockMultipartFile("file","drafts.csv","text/csv","name\nDraft".getBytes(StandardCharsets.UTF_8)));
            default -> throw new AssertionError(method);
        };
        if (!method.equals("FILE")) builder.contentType("application/json")
                .content(path.endsWith("/reject") ? "{\"reason\":\"Correct documents\"}"
                        : path.endsWith("/translations") ? "{\"nameTranslations\":{\"en\":\"New\"}}" : "{\"name\":\"New\"}");
        return builder;
    }
}
