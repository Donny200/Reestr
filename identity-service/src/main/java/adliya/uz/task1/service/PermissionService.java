package adliya.uz.task1.service;

import adliya.uz.task1.dto.CreatePermissionRequest;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.exception.PermissionAlreadyExistsException;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public Permission create(CreatePermissionRequest request) {
        if (permissionRepository.existsByCode(request.getCode())) {
            throw new PermissionAlreadyExistsException(
                    "Permission already exists with code: " + request.getCode());
        }

        Permission permission = Permission.builder()
                .code(request.getCode())
                .name(request.getName())
                .category(request.getCategory())
                .build();

        return permissionRepository.save(permission);
    }

    public List<Permission> getAll() {
        return permissionRepository.findAll();
    }

    public Permission getById(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found, ID: " + id));
    }
}
