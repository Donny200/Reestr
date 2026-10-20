package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import adliya.uz.functioncatalogservice.dto.EngagementEventRequest;
import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.repository.EngagementDailyCountRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EngagementTrackingService {

    private final OrgFunctionRepository functions;
    private final EngagementDailyCountRepository counts;
    private final EngagementRateLimiter rateLimiter;
    private final CatalogAccess access;
    private final AnalyticsProperties properties;

    @Transactional
    public boolean record(EngagementEventRequest request, String clientAddress) {
        requireSupportedSubject(request);
        if (access.authenticated()) {
            return false;
        }
        rateLimiter.acquire(clientAddress);
        Optional<Subject> subject = resolve(request);
        subject.ifPresent(value -> counts.increment(LocalDate.now(properties.getZone()), request.type().name(),
                value.organizationId(), value.functionId()));
        return subject.isPresent();
    }

    private Optional<Subject> resolve(EngagementEventRequest request) {
        if (request.serviceId() != null) {
            return functions.findByIdAndStatus(request.serviceId(), FunctionStatus.PUBLISHED)
                    .map(function -> new Subject(function.getOrganizationId(), function.getId()));
        }
        if (request.organizationId() != null) {
            return functions.existsByOrganizationIdAndStatus(request.organizationId(), FunctionStatus.PUBLISHED)
                    ? Optional.of(new Subject(request.organizationId(), null))
                    : Optional.empty();
        }
        return Optional.of(new Subject(null, null));
    }

    private static void requireSupportedSubject(EngagementEventRequest request) {
        if (!accepts(request.type(), request)) {
            throw new IllegalArgumentException(request.type() + " does not accept this subject");
        }
    }

    private static boolean accepts(EngagementEventType type, EngagementEventRequest request) {
        if (request.serviceId() != null) {
            return type.acceptsService();
        }
        if (request.organizationId() != null) {
            return type.acceptsOrganization();
        }
        return type.acceptsCatalog();
    }

    private record Subject(Long organizationId, Long functionId) {}
}
