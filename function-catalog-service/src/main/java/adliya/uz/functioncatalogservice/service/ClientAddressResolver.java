package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.ReportProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ClientAddressResolver {

    private final ReportProperties properties;

    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(properties.getClientAddressHeader());
        return StringUtils.hasText(header) ? header.trim() : request.getRemoteAddr();
    }
}
