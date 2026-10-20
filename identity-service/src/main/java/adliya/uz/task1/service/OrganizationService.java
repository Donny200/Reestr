package adliya.uz.task1.service;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.dto.CreateOrganizationRequest;
import adliya.uz.task1.dto.OfficialLink;
import adliya.uz.task1.dto.OrganizationContact;
import adliya.uz.task1.dto.UpdateOrganizationRequest;
import adliya.uz.task1.dto.UpdateOrganizationTranslationsRequest;
import adliya.uz.task1.entity.Language;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.TranslatedText;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.OrganizationAlreadyExistsException;
import adliya.uz.task1.exception.OrganizationHasActiveMembersException;
import adliya.uz.task1.exception.ResourceNotFoundException;
import adliya.uz.task1.repository.LanguageRepository;
import adliya.uz.task1.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.IllformedLocaleException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private static final String ORGANIZATIONS_CREATE = "ORGANIZATIONS_CREATE";
    private static final String ORGANIZATIONS_EDIT = "ORGANIZATIONS_EDIT";
    private static final String ORGANIZATIONS_EDIT_OWN = "ORGANIZATIONS_EDIT_OWN";
    private static final String ORGANIZATIONS_DEACTIVATE = "ORGANIZATIONS_DEACTIVATE";
    private static final String ORGANIZATIONS_REACTIVATE = "ORGANIZATIONS_REACTIVATE";
    private static final String FUNCTIONS_MANAGE_ANY_ORGANIZATION = "FUNCTIONS_MANAGE_ANY_ORGANIZATION";
    private static final Set<String> STAFF_ROLES = Set.of(
            SystemRole.ORG_ADMIN.authority(), SystemRole.MODERATOR.authority());
    private static final int NAME_MAX_LENGTH = 150;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final OrganizationRepository organizationRepository;
    private final OrganizationTranslationStateService translationStateService;
    private final UserService userService;
    private final LanguageRepository languageRepository;

    @Transactional
    public Organization create(CreateOrganizationRequest request) {
        requirePermission(ORGANIZATIONS_CREATE);
        if (organizationRepository.existsByName(request.getName())) {
            throw new OrganizationAlreadyExistsException("Organization already exists with name: " + request.getName());
        }
        Organization organization = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .officialSourceUrl(OfficialLink.normalize(request.getOfficialSourceUrl()))
                .enabled(true)
                .build();
        applyContact(organization, request.getContact());
        return organizationRepository.save(organization);
    }

    public Organization getById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found, ID: " + id));
    }

    public List<Organization> getAll() {
        return organizationRepository.findAll();
    }

    @Transactional
    public Organization update(Long id, UpdateOrganizationRequest request) {
        requireUpdateAccess(id);
        Organization org = getById(id);

        boolean nameChanged = request.getName() != null && !request.getName().equals(org.getName());
        boolean descriptionChanged = request.getDescription() != null
                && !Objects.equals(request.getDescription(), org.getDescription());

        if (nameChanged) {
            if (organizationRepository.existsByName(request.getName())) {
                throw new OrganizationAlreadyExistsException("Organization already exists with name: " + request.getName());
            }
            org.setName(request.getName());
        }
        if (descriptionChanged) {
            org.setDescription(request.getDescription());
        }
        boolean contactChanged = applyContact(org, request.getContact());
        boolean sourceChanged = false;
        if (request.getOfficialSourceUrl() != null) {
            String officialSourceUrl = OfficialLink.normalize(request.getOfficialSourceUrl());
            sourceChanged = !Objects.equals(officialSourceUrl, org.getOfficialSourceUrl());
            org.setOfficialSourceUrl(officialSourceUrl);
        }
        if ((nameChanged || contactChanged || sourceChanged) && org.getLastVerifiedAt() != null) {
            org.setVerificationOutdated(true);
        }
        translationStateService.invalidateMachineTranslations(org, nameChanged, descriptionChanged);
        return organizationRepository.save(org);
    }

    /**
     * Stores human translations of the official name and description. Organization names are official texts, so they
     * are never machine translated; staff enter them here, with the same access as editing the profile.
     */
    @Transactional
    public Organization updateTranslations(Long id, UpdateOrganizationTranslationsRequest request) {
        requireUpdateAccess(id);
        Organization org = getById(id);
        Set<String> active = languageRepository.findAllByActiveTrue().stream()
                .map(Language::getCode)
                .filter(Objects::nonNull)
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (request.nameTranslations() != null) {
            org.setNameTranslations(humanTranslations(request.nameTranslations(), active, NAME_MAX_LENGTH, "Name"));
        }
        if (request.descriptionTranslations() != null) {
            org.setDescriptionTranslations(humanTranslations(request.descriptionTranslations(), active,
                    DESCRIPTION_MAX_LENGTH, "Description"));
        }
        return organizationRepository.save(org);
    }

    /** Internal check by the responsible staff member; an external source link is optional supporting information. */
    @Transactional
    public Organization verify(Long id) {
        User current = requireUpdateAccess(id);
        Organization org = getById(id);
        org.setLastVerifiedAt(Instant.now());
        org.setVerifiedByUserId(current.getId());
        org.setVerificationOutdated(false);
        log.info("Organization {} verified by user {}", id, current.getId());
        return organizationRepository.save(org);
    }

    @Transactional
    public void deactivate(Long id) {
        requirePermission(ORGANIZATIONS_DEACTIVATE);
        Organization org = getById(id);

        boolean hasActiveStaff = org.getMembers().stream()
                .anyMatch(member -> Boolean.TRUE.equals(member.getEnabled())
                        && STAFF_ROLES.contains(member.getRole().getName()));
        if (hasActiveStaff) {
            throw new OrganizationHasActiveMembersException(
                    "Cannot deactivate organization while it still has an active org admin or moderator assigned. "
                            + "Reassign or deactivate them first.");
        }
        org.setEnabled(false);
        organizationRepository.save(org);
    }

    @Transactional
    public Organization reactivate(Long id) {
        requirePermission(ORGANIZATIONS_REACTIVATE);
        Organization organization = getById(id);
        organization.setEnabled(true);
        return organizationRepository.save(organization);
    }

    /** Enabled members of an organization; only its own staff, SUPER_ADMIN or global function managers may list them. */
    @Transactional(readOnly = true)
    public List<User> activeMembers(Long organizationId) {
        User current = requireEnabledUser();
        boolean global = SystemRole.SUPER_ADMIN.isHeldBy(current) || hasPermission(current, FUNCTIONS_MANAGE_ANY_ORGANIZATION);
        if (!global && !belongsTo(current, organizationId)) {
            throw new AccessDeniedException("You can only view staff of your own organization(s)");
        }
        Organization organization = getById(organizationId);
        return organization.getMembers().stream()
                .filter(member -> Boolean.TRUE.equals(member.getEnabled()))
                .sorted(java.util.Comparator.comparing(User::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(User::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Organization> getAllPublic() {
        return organizationRepository.findAllByEnabledTrue();
    }

    public Organization getPublicById(Long id) {
        Organization org = getById(id);
        if (!Boolean.TRUE.equals(org.getEnabled())) {
            throw new ResourceNotFoundException("Organization not found, ID: " + id);
        }
        return org;
    }

    private User requireUpdateAccess(Long organizationId) {
        User current = requireEnabledUser();
        if (hasPermission(current, ORGANIZATIONS_EDIT)) {
            return current;
        }
        if (!hasPermission(current, ORGANIZATIONS_EDIT_OWN) || !belongsTo(current, organizationId)) {
            throw new AccessDeniedException("You can only edit your own organization");
        }
        return current;
    }

    private static boolean applyContact(Organization organization, OrganizationContact contact) {
        if (contact == null) {
            return false;
        }
        if ((contact.latitude() == null) != (contact.longitude() == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together");
        }
        OrganizationContact before = OrganizationContact.of(organization);
        organization.setAddress(text(contact.address()));
        organization.setPhone(text(contact.phone()));
        organization.setWorkingHours(text(contact.workingHours()));
        organization.setLatitude(contact.latitude());
        organization.setLongitude(contact.longitude());
        organization.setMapUrl(OfficialLink.normalize(contact.mapUrl()));
        organization.setRegionCode(text(contact.regionCode()));
        return !before.equals(OrganizationContact.of(organization));
    }

    private static Map<String, TranslatedText> humanTranslations(Map<String, String> values, Set<String> activeLanguages,
                                                                 int maxLength, String field) {
        Map<String, TranslatedText> result = new LinkedHashMap<>();
        values.forEach((rawCode, rawText) -> {
            String code = languageCode(rawCode);
            if (!activeLanguages.contains(code)) {
                throw new IllegalArgumentException("Language is not enabled: " + rawCode);
            }
            String value = text(rawText);
            if (value == null) {
                return;
            }
            if (value.length() > maxLength) {
                throw new IllegalArgumentException(field + " translation (" + code + ") must be at most " + maxLength + " characters");
            }
            if (result.put(code, new TranslatedText(value, TranslatedText.HUMAN)) != null) {
                throw new IllegalArgumentException("Duplicate language: " + rawCode);
            }
        });
        return result;
    }

    /** Lower-case BCP 47 tag, the form the public site and function-catalog use to look translations up. */
    private static String languageCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Language code is required");
        }
        try {
            Locale locale = new Locale.Builder().setLanguageTag(raw.strip().replace('_', '-')).build();
            if (locale.getLanguage().isEmpty() || "und".equalsIgnoreCase(locale.toLanguageTag())) {
                throw new IllegalArgumentException("Invalid BCP 47 language tag: " + raw);
            }
            return locale.toLanguageTag().toLowerCase(Locale.ROOT);
        } catch (IllformedLocaleException exception) {
            throw new IllegalArgumentException("Invalid BCP 47 language tag: " + raw, exception);
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void requirePermission(String permissionCode) {
        if (!hasPermission(requireEnabledUser(), permissionCode)) {
            throw new AccessDeniedException("Missing required permission: " + permissionCode);
        }
    }

    private User requireEnabledUser() {
        User current = userService.getCurrentUser();
        if (!Boolean.TRUE.equals(current.getEnabled()) || current.getRole() == null) {
            throw new AccessDeniedException("An enabled user with an assigned role is required");
        }
        return current;
    }

    private static boolean belongsTo(User user, Long organizationId) {
        return user.getOrganizations().stream().map(Organization::getId).anyMatch(organizationId::equals);
    }

    private static boolean hasPermission(User user, String permissionCode) {
        return user.getRole().getPermissions().stream()
                .anyMatch(permission -> permissionCode.equals(permission.getCode()));
    }
}
