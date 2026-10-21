package adliya.uz.task1.service;

import adliya.uz.task1.dto.UpdateOrganizationRequest;
import adliya.uz.task1.dto.UpdateOrganizationTranslationsRequest;
import adliya.uz.task1.entity.Language;
import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.TranslatedText;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.LanguageRepository;
import adliya.uz.task1.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationTranslationsTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserService userService;
    @Mock
    private LanguageRepository languageRepository;

    private OrganizationService service;
    private Organization organization;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, new OrganizationTranslationStateService(), userService, languageRepository);
        organization = Organization.builder().id(10L).name("Adliya vazirligi").description("Davlat organi").enabled(true).build();
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(languageRepository.findAllByActiveTrue()).thenReturn(List.of(language("uz"), language("ru"), language("fr"), language("pt-BR")));
    }

    @Test
    void providedMapReplacesTheFieldWithHumanTranslations() {
        organization.getNameTranslations().put("ru", new TranslatedText("Старое", TranslatedText.MACHINE));
        organization.getNameTranslations().put("de", new TranslatedText("Alt", TranslatedText.HUMAN));
        organization.getDescriptionTranslations().put("ru", new TranslatedText("Описание", TranslatedText.HUMAN));
        actAs(user("ORGANIZATIONS_EDIT"));

        service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(
                Map.of("FR", "  Ministère de la Justice ", "pt_br", "Ministério da Justiça"), null));

        assertThat(organization.getNameTranslations())
                .containsOnlyKeys("fr", "pt-br")
                .containsEntry("fr", new TranslatedText("Ministère de la Justice", TranslatedText.HUMAN))
                .containsEntry("pt-br", new TranslatedText("Ministério da Justiça", TranslatedText.HUMAN));
        assertThat(organization.getDescriptionTranslations())
                .containsEntry("ru", new TranslatedText("Описание", TranslatedText.HUMAN));
    }

    @Test
    void blankValueRemovesTheLanguage() {
        organization.getDescriptionTranslations().put("fr", new TranslatedText("Organe d’État", TranslatedText.HUMAN));
        organization.getDescriptionTranslations().put("ru", new TranslatedText("Госорган", TranslatedText.HUMAN));
        actAs(user("ORGANIZATIONS_EDIT"));
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("fr", " ");
        descriptions.put("ru", "Государственный орган");

        service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(null, descriptions));

        assertThat(organization.getDescriptionTranslations())
                .containsOnlyKeys("ru")
                .containsEntry("ru", new TranslatedText("Государственный орган", TranslatedText.HUMAN));
    }

    @Test
    void invalidUnknownOrTooLongValuesAreRejected() {
        actAs(user("ORGANIZATIONS_EDIT"));

        assertThatThrownBy(() -> service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(Map.of("not a tag", "x"), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BCP 47");
        assertThatThrownBy(() -> service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(Map.of("de", "Justizministerium"), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not enabled");
        assertThatThrownBy(() -> service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(Map.of("fr", "x".repeat(151)), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("150");
        assertThatThrownBy(() -> service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(null, Map.of("fr", "x".repeat(501)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500");
        assertThatThrownBy(() -> service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(Map.of("fr", "A", "FR", "B"), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void ownOrganizationStaffMayTranslateButNotForeignOrganizations() {
        actAs(user("ORGANIZATIONS_EDIT_OWN", organization));
        service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(Map.of("fr", "Ministère"), null));
        assertThat(organization.getNameTranslations()).containsKey("fr");

        actAs(user("ORGANIZATIONS_EDIT_OWN", Organization.builder().id(20L).name("Other").build()));
        assertThatThrownBy(() -> service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(Map.of("fr", "X"), null)))
                .isInstanceOf(AccessDeniedException.class);

        actAs(user("ORGANIZATIONS_VIEW", organization));
        assertThatThrownBy(() -> service.updateTranslations(10L, new UpdateOrganizationTranslationsRequest(Map.of("fr", "X"), null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(organization.getNameTranslations().get("fr").text()).isEqualTo("Ministère");
    }

    @Test
    void renamingStillDropsOnlyMachineTranslations() {
        Map<String, TranslatedText> names = new LinkedHashMap<>();
        names.put("fr", new TranslatedText("Ministère", TranslatedText.HUMAN));
        names.put("ru", new TranslatedText("Министерство", TranslatedText.MACHINE));
        organization.setNameTranslations(names);
        actAs(user("ORGANIZATIONS_EDIT"));
        when(organizationRepository.existsByName("Yangi nom")).thenReturn(false);

        service.update(10L, UpdateOrganizationRequest.builder().name("Yangi nom").build());

        assertThat(organization.getNameTranslations()).containsOnlyKeys("fr");
    }

    private void actAs(User user) {
        when(userService.getCurrentUser()).thenReturn(user);
    }

    private static Language language(String code) {
        return Language.builder().code(code).active(true).build();
    }

    private static User user(String permission, Organization... organizations) {
        Role role = Role.builder().name("ROLE_CUSTOM").permissions(Set.of(Permission.builder().code(permission).build())).build();
        User user = User.builder().id(1L).email("actor@example.com").password("hash").role(role).enabled(true).build();
        user.getOrganizations().addAll(Set.of(organizations));
        return user;
    }
}
