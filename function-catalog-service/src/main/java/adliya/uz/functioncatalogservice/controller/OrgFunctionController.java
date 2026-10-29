package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.MachineTranslationSummary;
import adliya.uz.functioncatalogservice.service.MachineTranslationQueue;
import adliya.uz.functioncatalogservice.dto.OrgFunctionResponse;
import adliya.uz.functioncatalogservice.dto.PublicChangeResponse;
import adliya.uz.functioncatalogservice.service.PublicChangeService;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.UpdateRequirementsRequest;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.service.FunctionReviewService;
import adliya.uz.functioncatalogservice.service.OrgFunctionService;
import adliya.uz.functioncatalogservice.service.PublicSiteUrl;
import adliya.uz.functioncatalogservice.service.ServiceGuideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/functions")
@RequiredArgsConstructor
public class OrgFunctionController {

    private final OrgFunctionService orgFunctionService;
    private final PublicChangeService publicChanges;
    private final MachineTranslationQueue translations;
    private final ServiceGuideService guides;
    private final FunctionReviewService reviews;

    @org.springframework.beans.factory.annotation.Value("${catalog.public-base-url:}")
    private String publicBaseUrl;

    @GetMapping
    public ResponseEntity<List<OrgFunctionResponse>> getAll(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String category) {
        List<OrgFunction> functions;
        if (organizationId != null) {
            functions = orgFunctionService.getByOrganizationId(organizationId);
        } else if (category != null) {
            functions = orgFunctionService.getByCategory(category);
        } else {
            functions = orgFunctionService.getAll();
        }
        Map<Long, java.time.Instant> updated = publicChanges.lastUpdatedAt(functions.stream().map(OrgFunction::getId).toList());
        return ResponseEntity.ok(functions.stream().map(function -> OrgFunctionResponse.publicFrom(function, updated.get(function.getId()))).toList());
    }

    /**
     * Public facts about the site for printed material: the canonical public address (catalog.public-base-url) or
     * null when none is configured or it is a development address (localhost, private networks).
     */
    @GetMapping("/public-site")
    public Map<String, String> publicSite() {
        Map<String, String> result = new java.util.HashMap<>();
        result.put("publicBaseUrl", PublicSiteUrl.canonical(publicBaseUrl));
        return result;
    }

    /**
     * Public printable guide (PDF) of a published card in the requested interface language; no sign-in needed. Only
     * information published on this site is included and empty sections are omitted.
     */
    @GetMapping("/{id}/guide.pdf")
    public ResponseEntity<byte[]> guide(@PathVariable Long id, @RequestParam(required = false) String lang) {
        return guides.guide(id, lang).attachment();
    }

    /** Public, concise history of approved publications of a published card; nothing about drafts or staff. */
    @GetMapping("/{id}/history")
    public List<PublicChangeResponse> publicHistory(@PathVariable Long id) {
        return publicChanges.publicHistory(id).stream().map(PublicChangeResponse::from).toList();
    }

    /**
     * Staff list. Without {@code page}/{@code size} the whole scoped list is returned (existing contract); with them
     * one page ordered by id is returned and {@code X-Total-Count} carries the total.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('FUNCTIONS_VIEW')")
    public ResponseEntity<List<OrgFunctionResponse>> getAllForAdmin(@RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        if (page == null && size == null) {
            return ResponseEntity.ok(staffView(orgFunctionService.getAllForAdmin()));
        }
        var result = orgFunctionService.getPageForAdmin(Math.max(0, page == null ? 0 : page),
                Math.min(Math.max(1, size == null ? 50 : size), 200));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(staffView(result.getContent()));
    }

    private List<OrgFunctionResponse> staffView(List<OrgFunction> functions) {
        Map<Long, OrgFunction> revisions = orgFunctionService.draftRevisionsOf(functions);
        Map<Long, MachineTranslationSummary> summaries = translations.summaries(functions);
        Map<Long, Long> corrections = reviews.openCounts(functions.stream().map(OrgFunction::getId).toList());
        return functions.stream()
                .map(function -> OrgFunctionResponse.from(function, revisions.get(function.getId()))
                        .withMachineTranslation(summaries.get(function.getId()))
                        .withOpenCorrections(corrections.getOrDefault(function.getId(), 0L)))
                .toList();
    }

    @GetMapping("/{id}/admin")
    @PreAuthorize("hasAuthority('FUNCTIONS_VIEW')")
    public OrgFunctionResponse getForAdmin(@PathVariable Long id) {
        OrgFunction function = orgFunctionService.getForAdmin(id);
        return OrgFunctionResponse.from(function, orgFunctionService.draftRevisionsOf(List.of(function)).get(function.getId()))
                .withMachineTranslation(translations.summary(function))
                .withOpenCorrections(reviews.openCounts(List.of(function.getId())).getOrDefault(function.getId(), 0L));
    }

    /** Opens (or returns) the draft revision through which a published card is edited; the public version stays as is. */
    @PostMapping("/{id}/revisions")
    @PreAuthorize("hasAuthority('FUNCTIONS_EDIT')")
    public ResponseEntity<OrgFunctionResponse> createRevision(@PathVariable Long id) {
        return ResponseEntity.ok(OrgFunctionResponse.from(orgFunctionService.createRevision(id)));
    }

    @DeleteMapping("/{id}/revision")
    @PreAuthorize("hasAuthority('FUNCTIONS_EDIT')")
    public ResponseEntity<Void> discardRevision(@PathVariable Long id) {
        orgFunctionService.discardRevision(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrgFunctionResponse> getById(@PathVariable Long id) {
        OrgFunction function = orgFunctionService.getById(id);
        return ResponseEntity.ok(OrgFunctionResponse.publicFrom(function, publicChanges.lastUpdatedAt(List.of(function.getId())).get(function.getId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FUNCTIONS_CREATE')")
    public ResponseEntity<OrgFunctionResponse> create(@Valid @RequestBody CreateOrgFunctionRequest request) {
        OrgFunction function = orgFunctionService.create(request);
        return ResponseEntity.status(201).body(OrgFunctionResponse.from(function).withMachineTranslation(translations.summary(function)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FUNCTIONS_EDIT')")
    public ResponseEntity<OrgFunctionResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateOrgFunctionRequest request) {
        OrgFunction function = orgFunctionService.update(id, request);
        return ResponseEntity.ok(OrgFunctionResponse.from(function).withMachineTranslation(translations.summary(function)));
    }

    @PutMapping("/{id}/requirements")
    @PreAuthorize("hasAuthority('FUNCTIONS_MANAGE_REQUIREMENTS')")
    public ResponseEntity<OrgFunctionResponse> updateRequirements(
            @PathVariable Long id, @Valid @RequestBody UpdateRequirementsRequest request) {
        return ResponseEntity.ok(OrgFunctionResponse.from(orgFunctionService.updateRequirements(id, request.requirements())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FUNCTIONS_DEACTIVATE')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        orgFunctionService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
