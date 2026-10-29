package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.config.ReportProperties;
import adliya.uz.functioncatalogservice.config.SecurityConfig;
import adliya.uz.functioncatalogservice.security.JwtAuthenticationFilter;
import adliya.uz.functioncatalogservice.security.SimpleJwtService;
import adliya.uz.functioncatalogservice.service.ClientAddressResolver;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.PublicChangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A development address configured as the public base URL is never handed to printed pages. */
@WebMvcTest(controllers = OrgFunctionController.class, properties = {"eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false", "catalog.public-base-url=http://localhost:5173/"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ReportProperties.class, ClientAddressResolver.class})
class PublicSiteEndpointTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrgFunctionService functions;
    @MockitoBean PublicChangeService publicChanges;
    @MockitoBean adliya.uz.functioncatalogservice.service.MachineTranslationQueue translations;
    @MockitoBean SimpleJwtService jwt;
    @MockitoBean adliya.uz.functioncatalogservice.service.ServiceGuideService guides;
    @MockitoBean adliya.uz.functioncatalogservice.service.FunctionReviewService reviews;

    @Test void anonymousVisitorsReadThePublicSiteWithoutTheDevelopmentAddress() throws Exception {
        mvc.perform(get("/api/functions/public-site")).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicBaseUrl").doesNotExist());
    }
}
