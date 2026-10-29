package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.FunctionReviewResponse;
import adliya.uz.functioncatalogservice.dto.OrgFunctionResponse;
import adliya.uz.functioncatalogservice.dto.RequestChangesRequest;
import adliya.uz.functioncatalogservice.dto.ReviewCommentRequest;
import adliya.uz.functioncatalogservice.service.FunctionReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Field-level review of a submitted card. Reading is open to staff who can view the card (authors see the notes on
 * their fields); every change requires the review permission. Approval is the existing POST /{id}/publish.
 */
@RestController @RequestMapping("/api/functions/{id}") @RequiredArgsConstructor
public class FunctionReviewController {
    static final String REVIEWER = "hasAuthority('FUNCTIONS_REVIEW')";
    private final FunctionReviewService service;

    @GetMapping("/review") @PreAuthorize("hasAuthority('FUNCTIONS_VIEW')")
    public FunctionReviewResponse review(@PathVariable Long id) { return service.review(id); }

    @PostMapping("/review/comments") @PreAuthorize(REVIEWER)
    public FunctionReviewResponse comment(@PathVariable Long id, @Valid @RequestBody ReviewCommentRequest request) {
        return service.addComment(id, request.fieldPath(), request.comment());
    }

    @PostMapping("/review/comments/{commentId}/resolve") @PreAuthorize(REVIEWER)
    public FunctionReviewResponse resolve(@PathVariable Long id, @PathVariable Long commentId) {
        return service.resolveComment(id, commentId);
    }

    /** Reviewer checklist; publishers who are not reviewers can tick it too before approving. */
    @PutMapping("/review/sections/{section}") @PreAuthorize("hasAnyAuthority('FUNCTIONS_REVIEW', 'FUNCTIONS_PUBLISH')")
    public FunctionReviewResponse markReviewed(@PathVariable Long id, @PathVariable String section) {
        return service.markSection(id, section, true);
    }

    @DeleteMapping("/review/sections/{section}") @PreAuthorize("hasAnyAuthority('FUNCTIONS_REVIEW', 'FUNCTIONS_PUBLISH')")
    public FunctionReviewResponse unmarkReviewed(@PathVariable Long id, @PathVariable String section) {
        return service.markSection(id, section, false);
    }

    @PostMapping("/request-changes") @PreAuthorize(REVIEWER)
    public OrgFunctionResponse requestChanges(@PathVariable Long id, @Valid @RequestBody(required = false) RequestChangesRequest request) {
        return OrgFunctionResponse.from(service.requestChanges(id, request == null ? null : request.summary()));
    }
}
