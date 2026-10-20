package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.AssignRoleRequest;
import adliya.uz.task1.dto.CreateRoleRequest;
import adliya.uz.task1.dto.RoleResponse;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.RoleRepository;
import adliya.uz.task1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private static final int MAX_ROLE_NAME_LENGTH = 30;
    private static final Pattern NORMALIZED_ROLE_NAME =
            Pattern.compile("^ROLE_[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*$");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('ROLES_VIEW')")
    public List<RoleResponse> getAll() {
        return roleRepository.findAll().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('ROLES_VIEW')")
    public RoleResponse getById(Long id) {
        requirePositiveId(id, "Role ID");
        return RoleResponse.from(requireRole(id));
    }

    private Role requireRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found, ID: " + id));
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public RoleResponse create(CreateRoleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Role request is required");
        }
        String roleName = normalizeRoleName(request.getName());
        if (roleRepository.findByName(roleName).isPresent()) {
            throw new IllegalStateException("Role already exists: " + roleName);
        }

        Role role = Role.builder()
                .name(roleName)
                .build();
        return RoleResponse.from(roleRepository.save(role));
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(Long id) {
        requirePositiveId(id, "Role ID");
        Role role = requireRole(id);
        if (SystemRole.isSystemRole(role.getName())) {
            throw new IllegalStateException("System role cannot be deleted: " + role.getName());
        }
        if (userRepository.existsByRole_Id(role.getId())) {
            throw new IllegalStateException("Role cannot be deleted while it is assigned to users");
        }
        roleRepository.delete(role);
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String assignRoleToUser(AssignRoleRequest request, String actorEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Role assignment request is required");
        }
        requirePositiveId(request.getUserId(), "User ID");
        requirePositiveId(request.getRoleId(), "Role ID");
        if (actorEmail == null || actorEmail.isBlank()) {
            throw new AccessDeniedException("Authenticated actor is required");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found, ID: " + request.getUserId()));
        Role role = requireRole(request.getRoleId());

        if (role.getName().equals(SystemRole.SUPER_ADMIN.authority())
                && user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new AccessDeniedException("You cannot assign SUPER_ADMIN to yourself");
        }

        String previousRole = user.getRole().getName();
        boolean downgradesSuperAdmin = previousRole.equals(SystemRole.SUPER_ADMIN.authority())
                && !role.getName().equals(SystemRole.SUPER_ADMIN.authority());
        if (downgradesSuperAdmin
                && Boolean.TRUE.equals(user.getEnabled())
                && userRepository.countByRole_NameAndEnabledTrue(SystemRole.SUPER_ADMIN.authority()) <= 1) {
            throw new IllegalStateException("The last active SUPER_ADMIN cannot be downgraded");
        }

        user.setRole(role);
        userRepository.save(user);

        log.info(
                "audit_event=ROLE_ASSIGNED actor={} target_user_id={} target_email={} previous_role={} new_role={}",
                actorEmail,
                user.getId(),
                user.getEmail(),
                previousRole,
                role.getName()
        );

        return role.getName() + " assigned to " + user.getEmail() + " successfully.";
    }

    @Transactional(readOnly = true)
    public Role getByName(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
    }

    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('ROLES_MANAGE_PERMISSIONS')")
    public RoleResponse assignPermissions(Long roleId, Set<Long> permissionIds) {
        requirePositiveId(roleId, "Role ID");
        if (permissionIds == null) {
            throw new IllegalArgumentException("Permission IDs are required");
        }
        permissionIds.forEach(id -> requirePositiveId(id, "Permission ID"));

        Role role = requireRole(roleId);

        Set<Permission> permissions = permissionIds.stream()
                .map(permissionService::getById)
                .collect(Collectors.toCollection(HashSet::new));

        role.setPermissions(permissions);
        return RoleResponse.from(roleRepository.save(role));
    }

    private String normalizeRoleName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Role name is required");
        }
        String normalized = rawName.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]+", "_");
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        if (normalized.length() > MAX_ROLE_NAME_LENGTH
                || !NORMALIZED_ROLE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Role name must match ROLE_NAME and must not exceed 30 characters");
        }
        return normalized;
    }

    private void requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
