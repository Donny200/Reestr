package adliya.uz.task1.controller;

import adliya.uz.task1.dto.CreateOrganizationRequest;
import adliya.uz.task1.dto.OrganizationMemberResponse;
import adliya.uz.task1.dto.OrganizationResponse;
import adliya.uz.task1.dto.UpdateOrganizationRequest;
import adliya.uz.task1.dto.UpdateOrganizationTranslationsRequest;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANIZATIONS_CREATE')")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        Organization org = organizationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.from(org));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationResponse>> getAll() {
        List<OrganizationResponse> orgs = organizationService.getAll().stream()
                .map(OrganizationResponse::from)
                .toList();
        return ResponseEntity.ok(orgs);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(OrganizationResponse.from(organizationService.getById(id)));
    }

    /** Active users with access to the organization; visible to SUPER_ADMIN, global function managers and the organization's own staff. */
    @GetMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationMemberResponse>> members(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.activeMembers(id).stream().map(OrganizationMemberResponse::from).toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATIONS_EDIT') or hasAuthority('ORGANIZATIONS_EDIT_OWN')")
    public ResponseEntity<OrganizationResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(OrganizationResponse.from(organizationService.update(id, request)));
    }

    /** Human translations of the official name and description; the organization scope is checked in the service. */
    @PutMapping("/{id}/translations")
    @PreAuthorize("hasAuthority('ORGANIZATIONS_EDIT') or hasAuthority('ORGANIZATIONS_EDIT_OWN')")
    public ResponseEntity<OrganizationResponse> updateTranslations(@PathVariable Long id,
                                                                   @RequestBody UpdateOrganizationTranslationsRequest request) {
        return ResponseEntity.ok(OrganizationResponse.from(organizationService.updateTranslations(id, request)));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('ORGANIZATIONS_EDIT') or hasAuthority('ORGANIZATIONS_EDIT_OWN')")
    public ResponseEntity<OrganizationResponse> verify(@PathVariable Long id) {
        return ResponseEntity.ok(OrganizationResponse.from(organizationService.verify(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATIONS_DEACTIVATE')")
    public ResponseEntity<String> deactivate(@PathVariable Long id) {
        organizationService.deactivate(id);
        return ResponseEntity.ok("Organization deactivated successfully.");
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('ORGANIZATIONS_REACTIVATE')")
    public ResponseEntity<OrganizationResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(OrganizationResponse.from(organizationService.reactivate(id)));
    }
}
