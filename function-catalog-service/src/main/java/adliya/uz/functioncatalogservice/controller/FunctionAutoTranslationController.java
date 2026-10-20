package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.AutoTranslateRequest;
import adliya.uz.functioncatalogservice.dto.MachineTranslationSummary;
import adliya.uz.functioncatalogservice.dto.OrgFunctionResponse;
import adliya.uz.functioncatalogservice.dto.TranslationBackfillResponse;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.service.FunctionAutoTranslationService;
import adliya.uz.functioncatalogservice.service.MachineTranslationQueue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/functions")
@RequiredArgsConstructor
public class FunctionAutoTranslationController {
    static final String TRANSLATION_EDITOR = "hasAuthority('FUNCTIONS_EDIT') and hasAuthority('FUNCTIONS_TRANSLATIONS_EDIT')";

    private final FunctionAutoTranslationService service;
    private final MachineTranslationQueue queue;

    @GetMapping("/translation-capabilities")
    @PreAuthorize(TRANSLATION_EDITOR)
    public Capabilities capabilities() { return new Capabilities(service.available(), queue.automatic()); }

    /** Immediate translation on request; the background queue covers the same content without a click. */
    @PostMapping("/{id}/translate")
    @PreAuthorize(TRANSLATION_EDITOR)
    public OrgFunctionResponse translate(@PathVariable Long id, @Valid @RequestBody AutoTranslateRequest request) {
        OrgFunction function = service.translate(id, request);
        return OrgFunctionResponse.from(function).withMachineTranslation(queue.summary(function));
    }

    /** Puts the card back on the background queue (after a failure or a skipped run). */
    @PostMapping("/{id}/translation-job")
    @PreAuthorize(TRANSLATION_EDITOR)
    public ResponseEntity<MachineTranslationSummary> retry(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queue.retry(id));
    }

    /**
     * Bounded, idempotent backfill of drafts that lack translations in an active language; org-scoped. With
     * {@code includePublished=true} published cards are covered through draft revisions (never changed in place).
     */
    @PostMapping("/translations/backfill")
    @PreAuthorize(TRANSLATION_EDITOR)
    public ResponseEntity<TranslationBackfillResponse> backfill(@RequestParam(required = false) Long organizationId,
                                                                 @RequestParam(required = false) Integer limit,
                                                                 @RequestParam(defaultValue = "false") boolean includePublished) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(queue.backfill(organizationId, limit, includePublished));
    }

    /** {@code available}: a provider is configured; {@code automatic}: the background worker is also enabled. */
    public record Capabilities(boolean available, boolean automatic) {}
}
