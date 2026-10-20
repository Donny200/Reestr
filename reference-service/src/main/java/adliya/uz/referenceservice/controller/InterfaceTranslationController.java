package adliya.uz.referenceservice.controller;

import adliya.uz.referenceservice.dto.UpdateInterfaceTranslationsRequest;
import adliya.uz.referenceservice.dto.TranslationCoverageResponse;
import adliya.uz.referenceservice.service.InterfaceTranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interface-translations")
@RequiredArgsConstructor
public class InterfaceTranslationController {

    private final InterfaceTranslationService translationService;

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
}
