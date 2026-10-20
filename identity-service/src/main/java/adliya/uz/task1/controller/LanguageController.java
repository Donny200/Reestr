package adliya.uz.task1.controller;

import adliya.uz.task1.dto.AddLanguageRequest;
import adliya.uz.task1.dto.LanguageCatalogItem;
import adliya.uz.task1.dto.LanguageResponse;
import adliya.uz.task1.dto.LanguageSearchResult;
import adliya.uz.task1.service.LanguageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/languages")
@RequiredArgsConstructor
public class LanguageController {

    private final LanguageService languageService;

    @GetMapping("/catalog")
    public ResponseEntity<List<LanguageCatalogItem>> catalog() {
        return ResponseEntity.ok(languageService.getCatalog());
    }

    @GetMapping
    public ResponseEntity<List<LanguageResponse>> getActive() {
        return ResponseEntity.ok(languageService.getActive().stream().map(LanguageResponse::from).toList());
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('LANGUAGES_VIEW')")
    public ResponseEntity<List<LanguageSearchResult>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(languageService.search(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LANGUAGES_CREATE')")
    public ResponseEntity<LanguageResponse> add(@Valid @RequestBody AddLanguageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(LanguageResponse.from(languageService.add(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LANGUAGES_DELETE')")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        languageService.remove(id);
        return ResponseEntity.noContent().build();
    }
}
