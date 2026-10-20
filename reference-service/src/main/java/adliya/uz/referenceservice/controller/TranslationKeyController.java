package adliya.uz.referenceservice.controller;

import adliya.uz.referenceservice.dto.CreateTranslationKeyRequest;
import adliya.uz.referenceservice.dto.TranslationKeyResponse;
import adliya.uz.referenceservice.dto.UpdateTranslationKeyRequest;
import adliya.uz.referenceservice.service.TranslationKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/translation-keys")
@RequiredArgsConstructor
public class TranslationKeyController {

    private final TranslationKeyService translationKeyService;

    @GetMapping
    @PreAuthorize("hasAuthority('TRANSLATION_KEYS_VIEW')")
    public ResponseEntity<List<TranslationKeyResponse>> getAll() {
        return ResponseEntity.ok(translationKeyService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TRANSLATION_KEYS_CREATE')")
    public ResponseEntity<TranslationKeyResponse> create(
            @Valid @RequestBody CreateTranslationKeyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(translationKeyService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSLATION_KEYS_EDIT')")
    public ResponseEntity<TranslationKeyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTranslationKeyRequest request
    ) {
        return ResponseEntity.ok(translationKeyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSLATION_KEYS_DEACTIVATE')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        translationKeyService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
