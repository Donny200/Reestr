package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.CreateOrgAdminRequest;
import adliya.uz.task1.dto.PromoteToOrgAdminRequest;
import adliya.uz.task1.dto.UpdateOrgAdminRequest;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.EmailAlreadyExistsException;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String SUPER_ADMIN_ROLE = SystemRole.SUPER_ADMIN.authority();
    private static final String ORG_ADMIN_ROLE = SystemRole.ORG_ADMIN.authority();

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    @Transactional
    public User createOrgAdmin(CreateOrgAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("User with this email already exists: " + request.getEmail());
        }
        Organization org = organizationService.getById(request.getOrganizationId());
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleService.getByName(ORG_ADMIN_ROLE))
                .build();
        user.getOrganizations().add(org);
        return userRepository.save(user);
    }

    @Transactional
    public User promoteToOrgAdmin(PromoteToOrgAdminRequest request) {
        User user = findUser(request.getUserId());
        protectLastSuperAdmin(user);
        Organization org = organizationService.getById(request.getOrganizationId());
        user.setRole(roleService.getByName(ORG_ADMIN_ROLE));
        user.getOrganizations().add(org);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getPromotionCandidates() {
        return userRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> user.getRole() != null)
                .filter(user -> !SUPER_ADMIN_ROLE.equals(user.getRole().getName()))
                .filter(user -> !ORG_ADMIN_ROLE.equals(user.getRole().getName()))
                .toList();
    }

    public List<User> getAllOrgAdmins() {
        return userRepository.findAllByRole_Name(ORG_ADMIN_ROLE);
    }

    public User getOrgAdminById(Long id) {
        User user = findUser(id);
        if (!ORG_ADMIN_ROLE.equals(user.getRole().getName())) {
            throw new ResourceNotFoundException("User with ID " + user.getId() + " is not a " + ORG_ADMIN_ROLE);
        }
        return user;
    }

    @Transactional
    public User updateOrgAdmin(Long id, UpdateOrgAdminRequest request) {
        User user = getOrgAdminById(id);
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getOrganizationId() != null) {
            Organization org = organizationService.getById(request.getOrganizationId());
            user.getOrganizations().clear();
            user.getOrganizations().add(org);
        }
        if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
        User saved = userRepository.save(user);
        if (Boolean.FALSE.equals(request.getEnabled())) userService.endSessions(saved);
        return saved;
    }

    @Transactional
    public void deactivateOrgAdmin(Long id) {
        User user = getOrgAdminById(id);
        user.setEnabled(false);
        userRepository.save(user);
        userService.endSessions(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found, ID: " + id));
    }

    private void protectLastSuperAdmin(User user) {
        if (SuperAdmins.isLastEnabled(user, userRepository)) {
            throw new IllegalStateException("The last enabled SUPER_ADMIN cannot be downgraded");
        }
    }
}
