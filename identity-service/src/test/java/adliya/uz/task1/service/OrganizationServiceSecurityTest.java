package adliya.uz.task1.service;

import adliya.uz.task1.dto.OrganizationContact;
import adliya.uz.task1.dto.UpdateOrganizationRequest;
import adliya.uz.task1.entity.VerificationStatus;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceSecurityTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationTranslationStateService translationStateService;

    @Mock
    private UserService userService;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void globalEditPermissionCanEditAnyOrganization() {
        Organization foreignOrganization = organization(20L, true);
        User actor = user(1L, "ORGANIZATIONS_EDIT", organization(10L, true));
        when(userService.getCurrentUser()).thenReturn(actor);
        when(organizationRepository.findById(20L)).thenReturn(Optional.of(foreignOrganization));
        when(organizationRepository.save(foreignOrganization)).thenReturn(foreignOrganization);

        organizationService.update(20L, UpdateOrganizationRequest.builder().name("Updated").build());

        assertThat(foreignOrganization.getName()).isEqualTo("Updated");
        verify(organizationRepository).save(foreignOrganization);
    }

    @Test
    void editOwnPermissionCanEditAssignedOrganization() {
        Organization ownOrganization = organization(10L, true);
        User actor = user(1L, "ORGANIZATIONS_EDIT_OWN", ownOrganization);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(ownOrganization));
        when(organizationRepository.save(ownOrganization)).thenReturn(ownOrganization);

        organizationService.update(10L, UpdateOrganizationRequest.builder().description("Updated").build());

        assertThat(ownOrganization.getDescription()).isEqualTo("Updated");
        verify(organizationRepository).save(ownOrganization);
    }

    @Test
    void editOwnPermissionCannotEditForeignOrganization() {
        User actor = user(1L, "ORGANIZATIONS_EDIT_OWN", organization(10L, true));
        when(userService.getCurrentUser()).thenReturn(actor);

        assertThatThrownBy(() -> organizationService.update(
                20L,
                UpdateOrganizationRequest.builder().name("Forbidden").build()
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("own organization");

        verify(organizationRepository, never()).findById(20L);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void reactivateRequiresPermissionAndRestoresOrganization() {
        Organization disabled = organization(10L, false);
        User actor = user(1L, "ORGANIZATIONS_REACTIVATE");
        when(userService.getCurrentUser()).thenReturn(actor);
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(disabled));
        when(organizationRepository.save(disabled)).thenReturn(disabled);

        Organization result = organizationService.reactivate(10L);

        assertThat(result.getEnabled()).isTrue();
        verify(organizationRepository).save(disabled);
    }

    @Test
    void reactivateRejectsWrongPermission() {
        User actor = user(1L, "ORGANIZATIONS_EDIT");
        when(userService.getCurrentUser()).thenReturn(actor);

        assertThatThrownBy(() -> organizationService.reactivate(10L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORGANIZATIONS_REACTIVATE");

        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void verificationWorksWithoutAnExternalSourceAndRecordsTheVerifier() {
        Organization ownOrganization = organization(10L, true);
        ownOrganization.setOfficialSourceUrl(null);
        User actor = user(7L, "ORGANIZATIONS_EDIT_OWN", ownOrganization);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(ownOrganization));
        when(organizationRepository.save(ownOrganization)).thenReturn(ownOrganization);

        organizationService.verify(10L);

        assertThat(ownOrganization.getVerifiedByUserId()).isEqualTo(7L);
        assertThat(ownOrganization.getLastVerifiedAt()).isNotNull();
        assertThat(ownOrganization.verificationStatus(Instant.now())).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void editOwnPermissionCannotVerifyForeignOrganization() {
        User actor = user(1L, "ORGANIZATIONS_EDIT_OWN", organization(10L, true));
        when(userService.getCurrentUser()).thenReturn(actor);

        assertThatThrownBy(() -> organizationService.verify(20L)).isInstanceOf(AccessDeniedException.class);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void contactChangesAfterVerificationMarkItOutdatedButDescriptionDoesNot() {
        Organization verified = organization(10L, true);
        verified.setOfficialSourceUrl("https://gov.example/justice");
        verified.setLastVerifiedAt(Instant.now());
        User actor = user(1L, "ORGANIZATIONS_EDIT", verified);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(verified));
        when(organizationRepository.save(verified)).thenReturn(verified);

        organizationService.update(10L, UpdateOrganizationRequest.builder().description("New description").build());
        assertThat(verified.getVerificationOutdated()).isFalse();

        organizationService.update(10L, UpdateOrganizationRequest.builder()
                .contact(new OrganizationContact(" Tashkent, Sayilgoh 5 ", "+998 71 200-00-00", "Mon-Fri 09:00-18:00",
                        41.31, 69.27, null, "1726"))
                .build());

        assertThat(verified.getAddress()).isEqualTo("Tashkent, Sayilgoh 5");
        assertThat(verified.getLatitude()).isEqualTo(41.31);
        assertThat(verified.getVerificationOutdated()).isTrue();
        assertThat(verified.verificationStatus(Instant.now())).isEqualTo(VerificationStatus.OUTDATED);
    }

    @Test
    void unchangedContactKeepsVerificationAndPartialCoordinatesAreRejected() {
        Organization verified = organization(10L, true);
        verified.setAddress("Tashkent");
        verified.setLastVerifiedAt(Instant.now());
        User actor = user(1L, "ORGANIZATIONS_EDIT", verified);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(verified));
        when(organizationRepository.save(verified)).thenReturn(verified);

        organizationService.update(10L, UpdateOrganizationRequest.builder()
                .contact(new OrganizationContact("Tashkent", null, null, null, null, null, null)).build());
        assertThat(verified.getVerificationOutdated()).isFalse();

        assertThatThrownBy(() -> organizationService.update(10L, UpdateOrganizationRequest.builder()
                .contact(new OrganizationContact("Tashkent", null, null, 41.3, null, null, null)).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude and longitude");
    }

    private Organization organization(Long id, boolean enabled) {
        return Organization.builder()
                .id(id)
                .name("Organization " + id)
                .enabled(enabled)
                .build();
    }

    private User user(Long id, String permissionCode, Organization... organizations) {
        Role role = Role.builder()
                .name("ROLE_CUSTOM")
                .permissions(Set.of(Permission.builder().code(permissionCode).build()))
                .build();
        User user = User.builder()
                .id(id)
                .email("actor@example.com")
                .password("hash")
                .role(role)
                .enabled(true)
                .build();
        user.getOrganizations().addAll(Set.of(organizations));
        return user;
    }
}
