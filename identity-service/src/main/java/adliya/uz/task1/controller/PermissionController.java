package adliya.uz.task1.controller;

import adliya.uz.task1.dto.CreatePermissionRequest;
import adliya.uz.task1.dto.PermissionResponse;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    public ResponseEntity<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request) {
        Permission permission = permissionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PermissionResponse.from(permission));
    }

    @GetMapping
    public ResponseEntity<List<PermissionResponse>> getAll() {
        List<PermissionResponse> permissions = permissionService.getAll().stream()
                .map(PermissionResponse::from)
                .toList();
        return ResponseEntity.ok(permissions);
    }
}