package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.FunctionCategoryRequest;
import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.service.FunctionCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/functions/categories") @RequiredArgsConstructor
public class FunctionCategoryController {
    private final FunctionCategoryService service;
    @GetMapping public List<FunctionCategory> getAll() { return service.getAll(); }
    @GetMapping("/{id}") public FunctionCategory get(@PathVariable Long id) { return service.get(id); }
    @PostMapping @PreAuthorize("hasAuthority('FUNCTION_CATEGORIES_MANAGE')")
    public ResponseEntity<FunctionCategory> create(@Valid @RequestBody FunctionCategoryRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('FUNCTION_CATEGORIES_MANAGE')")
    public FunctionCategory update(@PathVariable Long id, @Valid @RequestBody FunctionCategoryRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('FUNCTION_CATEGORIES_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
}
