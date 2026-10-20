package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.EngagementEventRequest;
import adliya.uz.functioncatalogservice.service.ClientAddressResolver;
import adliya.uz.functioncatalogservice.service.EngagementTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Catalog engagement", description = "Anonymous, aggregate information-site engagement. "
        + "These counts are catalog views and link clicks, never government-service applications or completions.")
public class EngagementController {

    private final EngagementTrackingService tracking;
    private final ClientAddressResolver clientAddresses;

    @PostMapping("/events")
    @Operation(summary = "Count one public catalog view or link click",
            description = "Adds one to today's aggregate counter. The organization is derived from the stored service; "
                    + "an organization id is accepted only for organization-page events and only when the organization "
                    + "has published services. No visitor identifiers or IP addresses are stored. Signed-in staff are not counted.")
    public ResponseEntity<Void> record(@Valid @RequestBody EngagementEventRequest request, HttpServletRequest http) {
        tracking.record(request, clientAddresses.resolve(http));
        return ResponseEntity.accepted().build();
    }
}
