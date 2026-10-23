package adliya.uz.referenceservice.controller;

import adliya.uz.referenceservice.dto.InterfaceTranslationExport;
import adliya.uz.referenceservice.dto.InterfaceTranslationOverview;
import adliya.uz.referenceservice.dto.UpdateInterfaceTranslationsRequest;
import adliya.uz.referenceservice.dto.TranslationCoverageResponse;
import adliya.uz.referenceservice.service.InterfaceTranslationOverviewService;
import adliya.uz.referenceservice.service.InterfaceTranslationService;
import adliya.uz.referenceservice.service.InterfaceTranslationTransferService;
import adliya.uz.referenceservice.translation.InterfaceMachineTranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interface-translations")
@RequiredArgsConstructor
public class InterfaceTranslationController {

    private final InterfaceTranslationService translationService;
    private final InterfaceTranslationOverviewService overviewService;
    private final InterfaceMachineTranslationService machineTranslation;
    private final InterfaceTranslationTransferService transfer;

    @GetMapping("/{languageCode}")
    public ResponseEntity<Map<String, String>> get(@PathVariable String languageCode) {
        return ResponseEntity.ok(translationService.getTranslations(languageCode));
    }

    @PutMapping("/{languageCode}")
    @PreAuthorize("hasAuthority('TRANSLATIONS_EDIT')")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable String languageCode,
            @Valid @RequestBody UpdateInterfaceTranslationsRequest request
    ) {
        translationService.updateTranslations(languageCode, request.translations());
        return ResponseEntity.ok(translationService.getTranslations(languageCode));
    }

    @DeleteMapping("/{languageCode}/{key}")
    @PreAuthorize("hasAuthority('TRANSLATIONS_EDIT')")
    public ResponseEntity<Void> delete(@PathVariable String languageCode, @PathVariable String key) {
        translationService.deleteTranslation(languageCode, key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{languageCode}/exact")
    @PreAuthorize("hasAuthority('TRANSLATIONS_VIEW_COVERAGE')")
    public ResponseEntity<Map<String, String>> getExact(@PathVariable String languageCode) {
        return ResponseEntity.ok(translationService.getExactTranslations(languageCode));
    }

    @GetMapping("/{languageCode}/missing")
    @PreAuthorize("hasAuthority('TRANSLATIONS_VIEW_COVERAGE')")
    public ResponseEntity<List<String>> getMissing(@PathVariable String languageCode) {
        return ResponseEntity.ok(translationService.getMissingKeys(languageCode));
    }

    @GetMapping("/{languageCode}/coverage")
    @PreAuthorize("hasAuthority('TRANSLATIONS_VIEW_COVERAGE')")
    public ResponseEntity<TranslationCoverageResponse> getCoverage(@PathVariable String languageCode) {
        return ResponseEntity.ok(translationService.getCoverage(languageCode));
    }

    /** Coverage of every active language plus its machine translation job; never includes provider credentials. */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('TRANSLATIONS_VIEW_COVERAGE')")
    public ResponseEntity<InterfaceTranslationOverview> overview() {
        return ResponseEntity.ok(overviewService.overview());
    }

    /** JSON backup of the translations stored for one language tag (with provenance), restorable with the import below. */
    @GetMapping("/{languageCode}/export")
    @PreAuthorize("hasAuthority('TRANSLATIONS_VIEW_COVERAGE')")
    public ResponseEntity<InterfaceTranslationExport> export(@PathVariable String languageCode) {
        InterfaceTranslationExport export = transfer.export(languageCode, Instant.now());
        return attachment("interface-translations-" + export.languageCode() + ".json", export);
    }

    /** JSON backup of every language that has stored translations, including removed (inactive) ones. */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('TRANSLATIONS_VIEW_COVERAGE')")
    public ResponseEntity<InterfaceTranslationExport.Bundle> exportAll() {
        return attachment("interface-translations.json", transfer.exportAll(Instant.now()));
    }

    /**
     * Restores a {@link #export} file. Values people edited in this installation are kept unless {@code replaceHuman}
     * is set; see {@link InterfaceTranslationTransferService} for the rules.
     */
    @PostMapping("/{languageCode}/import")
    @PreAuthorize("hasAuthority('TRANSLATIONS_EDIT')")
    public ResponseEntity<InterfaceTranslationExport.ImportResult> importLanguage(
            @PathVariable String languageCode,
            @RequestBody InterfaceTranslationExport document,
            @RequestParam(defaultValue = "false") boolean replaceHuman
    ) {
        return ResponseEntity.ok(transfer.importLanguage(languageCode, document, replaceHuman, Instant.now()));
    }

    /** Restores a bundle from {@code GET /export}, language by language, with the same rules. */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('TRANSLATIONS_EDIT')")
    public ResponseEntity<List<InterfaceTranslationExport.ImportResult>> importAll(
            @RequestBody InterfaceTranslationExport.Bundle bundle,
            @RequestParam(defaultValue = "false") boolean replaceHuman
    ) {
        if (bundle == null || !InterfaceTranslationExport.FORMAT.equals(bundle.format()) || bundle.languages() == null) {
            throw new IllegalArgumentException("Not an interface translation bundle (format must be "
                    + InterfaceTranslationExport.FORMAT + ")");
        }
        Instant now = Instant.now();
        return ResponseEntity.ok(bundle.languages().stream()
                .map(language -> transfer.importLanguage(language.languageCode(), language, replaceHuman, now))
                .toList());
    }

    private static <T> ResponseEntity<T> attachment(String filename, T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /** Queues (or retries) filling the language's missing entries by machine translation; the worker does the work. */
    @PostMapping("/{languageCode}/machine-translation")
    @PreAuthorize("hasAuthority('TRANSLATIONS_EDIT')")
    public ResponseEntity<InterfaceTranslationOverview.Job> requeue(@PathVariable String languageCode) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(InterfaceTranslationOverview.Job.of(machineTranslation.requeue(languageCode, Instant.now())));
    }
}
