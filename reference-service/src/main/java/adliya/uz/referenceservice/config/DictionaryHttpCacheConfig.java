package adliya.uz.referenceservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.io.IOException;

/**
 * Public dictionaries are fetched on every page load: an ETag lets browsers revalidate and receive 304 instead of the
 * whole dictionary again, while {@code no-cache} makes them check on each load so edits show up immediately.
 */
@Configuration
public class DictionaryHttpCacheConfig {

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> dictionaryEtagFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                if ("GET".equals(request.getMethod())) {
                    response.setHeader("Cache-Control", "no-cache");
                }
                super.doFilterInternal(request, response, chain);
            }
        };
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/interface-translations/*");
        registration.setName("dictionaryEtagFilter");
        return registration;
    }
}
