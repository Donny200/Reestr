package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.FunctionImportResponse;
import adliya.uz.functioncatalogservice.service.FunctionImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/functions") @RequiredArgsConstructor
public class FunctionImportController {
    private final FunctionImportService service;
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FUNCTIONS_IMPORT')")
    public FunctionImportResponse importFile(@RequestPart("file") MultipartFile file) { return service.importFile(file); }
}
