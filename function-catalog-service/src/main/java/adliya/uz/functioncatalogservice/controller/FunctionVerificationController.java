package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.OrgFunctionResponse;
import adliya.uz.functioncatalogservice.service.FunctionVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/functions")
@RequiredArgsConstructor
public class FunctionVerificationController {

    private final FunctionVerificationService service;

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('FUNCTIONS_REVIEW')")
    public OrgFunctionResponse verify(@PathVariable Long id) {
        return OrgFunctionResponse.from(service.verify(id));
    }
}
