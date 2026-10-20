package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.CreateModeratorRequest;
import adliya.uz.task1.dto.PromoteToModeratorRequest;
import adliya.uz.task1.dto.UpdateModeratorRequest;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.EmailAlreadyExistsException;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModeratorService {

    private static final String SUPER_ADMIN_ROLE = SystemRole.SUPER_ADMIN.authority();
    private static final String ORG_ADMIN_ROLE = SystemRole.ORG_ADMIN.authority();
    private static final String MODERATOR_ROLE = SystemRole.MODERATOR.authority();

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    @Transactional
    public User create(CreateModeratorRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("User with this email already exists: " + request.getEmail());
        }
        Set<Organization> orgs = resolveOrganizations(request.getOrganizationIds());
        checkScopeOrThrow(userService.getCurrentUser(), orgs);

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleService.getByName(MODERATOR_ROLE))
                .build();
        user.getOrganizations().addAll(orgs);
        return userRepository.save(user);
    }

    @Transactional
    public User promote(PromoteToModeratorRequest request) {
        User user = findUser(request.getUserId());
        User current = userService.getCurrentUser();
        validatePromotionTarget(user, current);

        Set<Organization> orgs = resolveOrganizations(request.getOrganizationIds());
        checkScopeOrThrow(current, orgs);

        user.setRole(roleService.getByName(MODERATOR_ROLE));
        user.getOrganizations().clear();
        user.getOrganizations().addAll(orgs);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getPromotionCandidates() {
        User current = userService.getCurrentUser();
        boolean superAdmin = isSuperAdmin(current);
        Set<Long> myOrgIds = orgIdsOf(current);
        return userRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> user.getRole() != null)
                .filter(user -> !MODERATOR_ROLE.equals(user.getRole().getName()))
                .filter(user -> !SUPER_ADMIN_ROLE.equals(user.getRole().getName()))
                .filter(user -> superAdmin
                        || (!ORG_ADMIN_ROLE.equals(user.getRole().getName()) && isEntirelyWithinScope(user, myOrgIds)))
                .toList();
    }

    public List<User> getAll() {
        User current = userService.getCurrentUser();
        List<User> moderators = userRepository.findAllByRole_Name(MODERATOR_ROLE);
        if (isSuperAdmin(current)) {
            return moderators;
        }
        Set<Long> myOrgIds = orgIdsOf(current);
        return moderators.stream()
                .filter(moderator -> orgIdsOf(moderator).stream().anyMatch(myOrgIds::contains))
                .toList();
    }

    public User getById(Long id) {
        User moderator = findUser(id);
        requireModeratorInScope(moderator, userService.getCurrentUser());
        return moderator;
    }

    @Transactional
    public User update(Long id, UpdateModeratorRequest request) {
        User moderator = findUser(id);
        User current = userService.getCurrentUser();
        requireModeratorInScope(moderator, current);

        Set<Organization> newOrgs = resolveOrganizations(request.getOrganizationIds());
        checkScopeOrThrow(current, newOrgs);

        if (request.getFirstName() != null) moderator.setFirstName(request.getFirstName());
        if (request.getLastName() != null) moderator.setLastName(request.getLastName());
        if (request.getPhone() != null) moderator.setPhone(request.getPhone());

        moderator.getOrganizations().clear();
        moderator.getOrganizations().addAll(newOrgs);

        if (request.getEnabled() != null) moderator.setEnabled(request.getEnabled());

        User saved = userRepository.save(moderator);
        if (Boolean.FALSE.equals(request.getEnabled())) userService.endSessions(saved);
        return saved;
    }

    @Transactional
    public void deactivate(Long id) {
        User moderator = getById(id);
        moderator.setEnabled(false);
        userRepository.save(moderator);
        userService.endSessions(moderator);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found, ID: " + id));
    }

    private void requireModeratorInScope(User moderator, User current) {
        requireRole(moderator, MODERATOR_ROLE);
        checkScopeOrThrow(current, moderator.getOrganizations());
    }

    private Set<Organization> resolveOrganizations(Set<Long> ids) {
        return ids.stream()
                .map(organizationService::getById)
                .collect(Collectors.toSet());
    }

    private void checkScopeOrThrow(User current, Set<Organization> orgs) {
        if (isSuperAdmin(current)) {
            return;
        }
        Set<Long> myOrgIds = orgIdsOf(current);
        boolean allowed = orgs.stream().map(Organization::getId).allMatch(myOrgIds::contains);
        if (!allowed) {
            throw new AccessDeniedException("You can only manage moderators within your own organization(s)");
        }
    }

    private void validatePromotionTarget(User target, User current) {
        if (target.getRole() == null) {
            throw new IllegalStateException("User does not have an assigned role");
        }
        if (MODERATOR_ROLE.equals(target.getRole().getName())) {
            throw new IllegalStateException("User is already a moderator");
        }
        if (SUPER_ADMIN_ROLE.equals(target.getRole().getName())) {
            if (!isSuperAdmin(current)) {
                throw new AccessDeniedException("Only SUPER_ADMIN can change another SUPER_ADMIN role");
            }
            if (SuperAdmins.isLastEnabled(target, userRepository)) {
                throw new IllegalStateException("The last enabled SUPER_ADMIN cannot be downgraded");
            }
        }
        if (!isSuperAdmin(current)) {
            if (ORG_ADMIN_ROLE.equals(target.getRole().getName())) {
                throw new AccessDeniedException("ORG_ADMIN cannot downgrade another ORG_ADMIN");
            }
            if (!isEntirelyWithinScope(target, orgIdsOf(current))) {
                throw new AccessDeniedException("You can only promote users in your organization scope");
            }
        }
    }

    private boolean isEntirelyWithinScope(User user, Set<Long> allowedOrganizationIds) {
        Set<Long> targetOrganizationIds = orgIdsOf(user);
        return !targetOrganizationIds.isEmpty() && allowedOrganizationIds.containsAll(targetOrganizationIds);
    }

    private Set<Long> orgIdsOf(User user) {
        return user.getOrganizations().stream()
                .map(Organization::getId)
                .collect(Collectors.toSet());
    }

    private static boolean isSuperAdmin(User user) {
        return SystemRole.SUPER_ADMIN.isHeldBy(user);
    }

    private void requireRole(User user, String expectedRole) {
        if (!expectedRole.equals(user.getRole().getName())) {
            throw new ResourceNotFoundException("User with ID " + user.getId() + " is not a " + expectedRole);
        }
    }
}
