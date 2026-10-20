package adliya.uz.task1.controller;

import adliya.uz.task1.dto.AssignPermissionsToRoleRequest;
import adliya.uz.task1.dto.AssignRoleRequest;
import adliya.uz.task1.dto.CreateRoleRequest;
import adliya.uz.task1.dto.RoleResponse;
import adliya.uz.task1.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('ROLES_VIEW')")
    public ResponseEntity<List<RoleResponse>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('ROLES_VIEW')")
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.ok("Role deleted successfully.");
    }

    @PostMapping("/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> assignRole(
            @Valid @RequestBody AssignRoleRequest request,
            Authentication authentication) {
        String result = roleService.assignRoleToUser(request, authentication.getName());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('ROLES_MANAGE_PERMISSIONS')")
    public ResponseEntity<RoleResponse> assignPermissions(
            @PathVariable Long id,
            @Valid @RequestBody AssignPermissionsToRoleRequest request) {
        return ResponseEntity.ok(roleService.assignPermissions(id, request.getPermissionIds()));
    }

}
