package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.*;
import adliya.uz.functioncatalogservice.entity.*;
import adliya.uz.functioncatalogservice.repository.*;
import adliya.uz.functioncatalogservice.security.*;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.*;

@ExtendWith(MockitoExtension.class)
class OrgFunctionServiceTest {
    @Mock OrgFunctionRepository orgFunctionRepository;
    @Mock OrgFunctionTranslationService translationService;
    @Mock FunctionCategoryService categories;
    @Spy CatalogAccess access = new CatalogAccess();
    @Mock AuditWriter audit;
    @Mock AuditLogRepository auditLogs;
    @Mock QualityReminderRepository reminders;
    @Mock IdentityOrganizationClient organizations;
    @Mock ReportReviewService reports;
    @Mock PublicChangeService publicChanges;
    @Mock MachineTranslationQueue translations;
    @InjectMocks OrgFunctionService service;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void publicQueriesStrictlyUsePublished() {
        service.getAll(); service.getByOrganizationId(10L); service.getByCategory("Registry");
        assertThatThrownBy(() -> service.getById(1L)).isInstanceOf(NoSuchElementException.class);
        verify(orgFunctionRepository).findAllByStatus(PUBLISHED);
        verify(orgFunctionRepository).findAllByOrganizationIdAndStatus(10L, PUBLISHED);
        verify(orgFunctionRepository).findAllByFunctionCategory_NameAndStatus("Registry", PUBLISHED);
        verify(orgFunctionRepository).findByIdAndStatus(1L, PUBLISHED);
        verify(orgFunctionRepository, never()).findAll();
    }

    static Stream<Arguments> transitions() {
        return Stream.of(
                new Object[]{"submit", EnumSet.of(DRAFT, DEACTIVATED), PENDING_REVIEW, AuditAction.SUBMIT_REVIEW},
                new Object[]{"withdraw", EnumSet.of(PENDING_REVIEW), DRAFT, AuditAction.WITHDRAW},
                new Object[]{"reject", EnumSet.of(PENDING_REVIEW), DRAFT, AuditAction.REJECT},
                new Object[]{"publish", EnumSet.of(PENDING_REVIEW), PUBLISHED, AuditAction.PUBLISH},
                new Object[]{"reactivate", EnumSet.of(DEACTIVATED), PENDING_REVIEW, AuditAction.REACTIVATE},
                new Object[]{"deactivate", EnumSet.of(PUBLISHED), DEACTIVATED, AuditAction.DEACTIVATE}
        ).flatMap(t -> Arrays.stream(FunctionStatus.values())
                .map(actual -> Arguments.of(t[0], t[1], t[2], t[3], actual)));
    }

    @ParameterizedTest @MethodSource("transitions")
    void enforcesTransitionMatrix(String operation, Set<FunctionStatus> expected, FunctionStatus target,
                                  AuditAction action, FunctionStatus actual) {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of());
        var function = function(actual, 10L);
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(function));
        if (expected.contains(actual)) {
            execute(operation);
            assertThat(function.getStatus()).isEqualTo(target);
            assertThat(function.getActive()).isEqualTo(target == PUBLISHED);
            verify(audit).function(eq(function), eq(action), eq(operation.equals("reject") ? "Clarify documents" : actual + " -> " + target));
        } else {
            assertThatThrownBy(() -> execute(operation)).isInstanceOf(WorkflowConflictException.class);
            assertThat(function.getStatus()).isEqualTo(actual);
            verifyNoInteractions(audit);
        }
    }

    @Test void queueAndAdminListAreScoped() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of("FUNCTIONS_REVIEW"));
        service.getAllForAdmin(); service.pendingReview();
        verify(orgFunctionRepository).findAllByOrganizationIdIn(List.of(10L));
        verify(orgFunctionRepository).findAllByStatusAndOrganizationIdIn(PENDING_REVIEW, List.of(10L));
        verify(orgFunctionRepository, never()).findAll();
    }
    @Test void globalEditorSeesAllOrganizations() {
        authenticate("ROLE_CUSTOM", List.of(), List.of("FUNCTIONS_MANAGE_ANY_ORGANIZATION"));
        service.getAllForAdmin(); service.pendingReview();
        verify(orgFunctionRepository).findAll(); verify(orgFunctionRepository).findAllByStatus(PENDING_REVIEW);
    }
    @Test void foreignAuditIsDenied() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of("AUDIT_VIEW"));
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(function(DRAFT, 20L)));
        assertThatThrownBy(() -> service.history(1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(auditLogs);
    }
    @Test void unassignedDraftRequiresGlobalScope() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of("FUNCTIONS_CREATE"));
        assertThatThrownBy(() -> service.create(new CreateOrgFunctionRequest("Draft", null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(audit);
    }
    @Test void pendingAndPublishedCannotBeEditedInPlace() {
        authenticate("ROLE_SUPER_ADMIN", List.of(), List.of());
        for (var status : List.of(PENDING_REVIEW, PUBLISHED)) {
            var function = function(status, 10L);
            when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(function));
            assertThatThrownBy(() -> service.updateRequirements(1L, "Changed")).isInstanceOf(WorkflowConflictException.class);
            assertThatThrownBy(() -> service.update(1L, new UpdateOrgFunctionRequest("Renamed", null, null, null, null, null, null)))
                    .isInstanceOf(WorkflowConflictException.class);
            assertThat(function.getName()).isEqualTo("Draft");
            assertThat(function.getRequirements()).isNull();
        }
        verifyNoInteractions(audit);
    }
    @Test void deactivatedCardsAreEditedInPlaceAndCanBeResubmitted() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of());
        var function = function(DEACTIVATED, 10L);
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(function));
        when(orgFunctionRepository.saveAndFlush(function)).thenReturn(function);
        service.updateRequirements(1L, "Updated documents");
        assertThat(function.getRequirements()).isEqualTo("Updated documents");
        service.submitForReview(1L);
        assertThat(function.getStatus()).isEqualTo(PENDING_REVIEW);
        verify(audit).function(function, AuditAction.SUBMIT_REVIEW, "DEACTIVATED -> PENDING_REVIEW");
    }
    @Test void publishedCardsAreEditedThroughADraftRevisionThatCopiesTheContent() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of("FUNCTIONS_EDIT"));
        var published = function(PUBLISHED, 10L);
        published.setDescription("Public text");
        published.setFee("Free");
        published.setOfficialSourceUrl("https://gov.example/x");
        published.setNameTranslations(new LinkedHashMap<>(Map.of("ru", new TranslatedText("Услуга", TranslatedText.HUMAN))));
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(published));
        when(orgFunctionRepository.findByRevisionOfId(1L)).thenReturn(Optional.empty());
        when(orgFunctionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            OrgFunction saved = invocation.getArgument(0); saved.setId(2L); return saved;
        });

        var revision = service.createRevision(1L);

        assertThat(revision.getId()).isEqualTo(2L);
        assertThat(revision.getStatus()).isEqualTo(DRAFT);
        assertThat(revision.getRevisionOfId()).isEqualTo(1L);
        assertThat(revision.isRevision()).isTrue();
        assertThat(revision.getName()).isEqualTo("Draft");
        assertThat(revision.getDescription()).isEqualTo("Public text");
        assertThat(revision.getFee()).isEqualTo("Free");
        assertThat(revision.getOfficialSourceUrl()).isEqualTo("https://gov.example/x");
        assertThat(revision.getNameTranslations()).isEqualTo(published.getNameTranslations());
        assertThat(revision.getNameTranslations()).isNotSameAs(published.getNameTranslations());
        assertThat(published.getStatus()).isEqualTo(PUBLISHED);
        verify(audit).revision(eq(published), eq(revision), eq(AuditAction.REVISION_CREATE), contains("public content unchanged"));
    }
    @Test void aSecondRevisionRequestReturnsTheExistingDraft() {
        authenticate("ROLE_SUPER_ADMIN", List.of(), List.of());
        var published = function(PUBLISHED, 10L);
        var existing = OrgFunction.builder().id(2L).name("Edited").organizationId(10L).status(PENDING_REVIEW).revisionOfId(1L).build();
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(published));
        when(orgFunctionRepository.findByRevisionOfId(1L)).thenReturn(Optional.of(existing));
        assertThat(service.createRevision(1L)).isSameAs(existing);
        verify(orgFunctionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }
    @Test void revisionsOnlyExistForPublishedCards() {
        authenticate("ROLE_SUPER_ADMIN", List.of(), List.of());
        for (var status : List.of(DRAFT, PENDING_REVIEW, DEACTIVATED)) {
            when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(function(status, 10L)));
            assertThatThrownBy(() -> service.createRevision(1L)).isInstanceOf(WorkflowConflictException.class);
        }
        var revision = function(DRAFT, 10L); revision.setRevisionOfId(7L);
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(revision));
        assertThatThrownBy(() -> service.createRevision(1L)).isInstanceOf(WorkflowConflictException.class);
        verify(orgFunctionRepository, never()).saveAndFlush(any());
    }
    @Test void organizationStaffCannotOpenRevisionsOfForeignCardsButSuperAdminCan() {
        var foreign = function(PUBLISHED, 20L);
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(foreign));
        authenticate("ROLE_CUSTOM", List.of(10L), List.of("FUNCTIONS_EDIT"));
        assertThatThrownBy(() -> service.createRevision(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.discardRevision(1L)).isInstanceOf(AccessDeniedException.class);
        verify(orgFunctionRepository, never()).findByRevisionOfId(any());

        authenticate("ROLE_SUPER_ADMIN", List.of(), List.of());
        when(orgFunctionRepository.findByRevisionOfId(1L)).thenReturn(Optional.empty());
        when(orgFunctionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            OrgFunction saved = invocation.getArgument(0); saved.setId(3L); return saved;
        });
        assertThat(service.createRevision(1L).getOrganizationId()).isEqualTo(20L);
    }
    @Test void approvedRevisionReplacesPublishedContentAndKeepsThePublishedId() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of("FUNCTIONS_PUBLISH"));
        var published = function(PUBLISHED, 10L);
        published.setDescription("Old public text");
        published.setSeedKey("seed-1");
        var revision = OrgFunction.builder().id(2L).name("New name").description("New text").organizationId(10L)
                .status(PENDING_REVIEW).revisionOfId(1L).fee("10 000 UZS").build();
        when(orgFunctionRepository.findById(2L)).thenReturn(Optional.of(revision));
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(published));

        var result = service.publish(2L);

        assertThat(result).isSameAs(published);
        assertThat(published.getId()).isEqualTo(1L);
        assertThat(published.getStatus()).isEqualTo(PUBLISHED);
        assertThat(published.getName()).isEqualTo("New name");
        assertThat(published.getDescription()).isEqualTo("New text");
        assertThat(published.getFee()).isEqualTo("10 000 UZS");
        assertThat(published.getSeedKey()).isEqualTo("seed-1");
        var order = inOrder(auditLogs, reminders, orgFunctionRepository, audit);
        order.verify(orgFunctionRepository).saveAndFlush(published);
        order.verify(auditLogs).copyFunctionLinks(2L, 1L);
        order.verify(auditLogs).deleteFunctionLinks(2L);
        order.verify(reminders).deleteAllByFunctionId(2L);
        order.verify(orgFunctionRepository).delete(revision);
        order.verify(audit).function(eq(published), eq(AuditAction.PUBLISH), contains("Revision #2 approved"));
        verify(organizations).requireExisting(10L);
        verifyNoInteractions(reports);
    }
    @Test void revisionPublicationIsBlockedUntilReviewAndWhenThePublishedCardIsUnderReview() {
        authenticate("ROLE_SUPER_ADMIN", List.of(), List.of());
        var draft = OrgFunction.builder().id(2L).name("Edit").organizationId(10L).status(DRAFT).revisionOfId(1L).build();
        when(orgFunctionRepository.findById(2L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.publish(2L)).isInstanceOf(WorkflowConflictException.class);
        draft.setStatus(PENDING_REVIEW);
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(function(PENDING_REVIEW, 10L)));
        assertThatThrownBy(() -> service.publish(2L)).isInstanceOf(WorkflowConflictException.class);
        assertThat(draft.getStatus()).isEqualTo(PENDING_REVIEW);
        verify(orgFunctionRepository, never()).delete(any());
        verifyNoInteractions(audit);
    }
    @Test void discardingRequiresADraftRevisionAndLeavesThePublishedCardAlone() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of("FUNCTIONS_EDIT"));
        var published = function(PUBLISHED, 10L);
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(published));
        when(orgFunctionRepository.findByRevisionOfId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.discardRevision(1L)).isInstanceOf(NoSuchElementException.class);

        var pending = OrgFunction.builder().id(2L).name("Edit").organizationId(10L).status(PENDING_REVIEW).revisionOfId(1L).build();
        when(orgFunctionRepository.findByRevisionOfId(1L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.discardRevision(1L)).isInstanceOf(WorkflowConflictException.class);
        verify(orgFunctionRepository, never()).delete(any());

        pending.setStatus(DRAFT);
        service.discardRevision(1L);
        verify(orgFunctionRepository).delete(pending);
        verify(audit).function(eq(published), eq(AuditAction.REVISION_DISCARD), contains("public content unchanged"));
        assertThat(published.getStatus()).isEqualTo(PUBLISHED);
        assertThat(published.getName()).isEqualTo("Draft");
    }
    @Test void draftRevisionsAreLookedUpForPublishedCardsOnly() {
        var published = function(PUBLISHED, 10L);
        var revision = OrgFunction.builder().id(2L).name("Edit").organizationId(10L).status(DRAFT).revisionOfId(1L).build();
        when(orgFunctionRepository.findAllByRevisionOfIdIn(List.of(1L))).thenReturn(List.of(revision));
        assertThat(service.draftRevisionsOf(List.of(published, revision))).containsExactly(Map.entry(1L, revision));
        assertThat(service.draftRevisionsOf(List.of(revision))).isEmpty();
    }
    @Test void translationEditRequiresBothPermissions() {
        authenticate("ROLE_SUPER_ADMIN", List.of(), List.of("FUNCTIONS_TRANSLATIONS_EDIT"));
        assertThatThrownBy(() -> service.updateTranslations(1L, new FunctionTranslationsRequest(Map.of("en","New"),null)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("FUNCTIONS_EDIT");
        verifyNoInteractions(orgFunctionRepository);
    }
    @Test void absentPrincipalFailsClosed() {
        when(orgFunctionRepository.findById(1L)).thenReturn(Optional.of(function(PUBLISHED, 10L)));
        assertThatThrownBy(() -> service.deactivate(1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(audit);
    }
    @Test void rejectionRequiresReason() {
        assertThatThrownBy(() -> service.reject(1L, " ")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orgFunctionRepository);
    }
    @Test void creationIsDraftAndAudited() {
        authenticate("ROLE_CUSTOM", List.of(10L), List.of());
        when(orgFunctionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            OrgFunction f = invocation.getArgument(0); f.setId(8L); return f;
        });
        var function = service.create(new CreateOrgFunctionRequest("New", "Description", 10L, "Documents", null));
        assertThat(function.getStatus()).isEqualTo(DRAFT);
        assertThat(function.getActive()).isFalse();
        verify(organizations).requireExisting(10L);
        verify(audit).function(function, AuditAction.CREATE, "Draft created");
    }

    private void execute(String operation) {
        switch (operation) {
            case "submit" -> service.submitForReview(1L);
            case "withdraw" -> service.withdraw(1L);
            case "reject" -> service.reject(1L, "Clarify documents");
            case "publish" -> service.publish(1L);
            case "reactivate" -> service.reactivate(1L);
            case "deactivate" -> service.deactivate(1L);
            default -> throw new AssertionError(operation);
        }
    }
    private OrgFunction function(FunctionStatus status, Long organizationId) {
        return OrgFunction.builder().id(1L).name("Draft").organizationId(organizationId).status(status).build();
    }
    private void authenticate(String role, List<Long> orgs, List<String> permissions) {
        var principal = new JwtPrincipal("editor@example.com", role, orgs, permissions, 42L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, permissions.stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
