package adliya.uz.task1.controller;

import adliya.uz.task1.dto.PublicOrganizationResponse;
import adliya.uz.task1.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/organizations")
@RequiredArgsConstructor
public class PublicOrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<List<PublicOrganizationResponse>> getAll() {
        List<PublicOrganizationResponse> orgs = organizationService.getAllPublic().stream()
                .map(PublicOrganizationResponse::from)
                .toList();
        return ResponseEntity.ok(orgs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicOrganizationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(PublicOrganizationResponse.from(organizationService.getPublicById(id)));
    }
}
