package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.AuditLogResponse;
import adliya.uz.functioncatalogservice.dto.CreateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.dto.FunctionTranslationsRequest;
import adliya.uz.functioncatalogservice.dto.LanguageTranslationRequest;
import adliya.uz.functioncatalogservice.dto.OfficialSourceUrl;
import adliya.uz.functioncatalogservice.dto.ServiceInstructions;
import adliya.uz.functioncatalogservice.dto.UpdateOrgFunctionRequest;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import adliya.uz.functioncatalogservice.entity.FunctionCategory;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.entity.TranslationTarget;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.AuditLogRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.QualityReminderRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static adliya.uz.functioncatalogservice.entity.FunctionStatus.DEACTIVATED;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.DRAFT;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.PENDING_REVIEW;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.PUBLISHED;

@Service
@RequiredArgsConstructor
public class OrgFunctionService {

    private static final String DEFAULT_SOURCE_LANGUAGE = "en";
    private static final int LANGUAGE_CODE_MAX_LENGTH = 35;
    private static final int REJECTION_REASON_MAX_LENGTH = 2000;
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2,3}(-[a-z0-9]{2,8})*");
    private static final Set<String> VERIFIED_FIELDS = Set.of("name", "description", "organizationId", "requirements", "officialSourceUrl",
            "documentDetails");

    private final OrgFunctionRepository orgFunctionRepository;
    private final OrgFunctionTranslationService translationService;
    private final FunctionCategoryService categories;
    private final CatalogAccess access;
    private final AuditWriter audit;
    private final AuditLogRepository auditLogs;
    private final QualityReminderRepository reminders;
    private final IdentityOrganizationClient organizations;
    private final ReportReviewService reports;
    private final PublicChangeService publicChanges;
    private final MachineTranslationQueue translations;

    public List<OrgFunction> getAll() {
        return orgFunctionRepository.findAllByStatus(PUBLISHED);
    }

    public OrgFunction getById(Long id) {
        return orgFunctionRepository.findByIdAndStatus(id, PUBLISHED).orElseThrow(() -> notFound(id));
    }

    public List<OrgFunction> getByOrganizationId(Long id) {
        return orgFunctionRepository.findAllByOrganizationIdAndStatus(id, PUBLISHED);
    }

    public List<OrgFunction> getByCategory(String category) {
        return orgFunctionRepository.findAllByFunctionCategory_NameAndStatus(category, PUBLISHED);
    }

    public List<OrgFunction> getAllForAdmin() {
        return access.global()
                ? orgFunctionRepository.findAll()
                : orgFunctionRepository.findAllByOrganizationIdIn(access.principal().organizationIds());
    }

    /** One page of the staff list (ordered by id) for callers that opt into server-side paging. */
    public org.springframework.data.domain.Page<OrgFunction> getPageForAdmin(int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("id"));
        return access.global()
                ? orgFunctionRepository.findAll(pageable)
                : orgFunctionRepository.findAllByOrganizationIdIn(access.principal().organizationIds(), pageable);
    }

    public OrgFunction getForAdmin(Long id) {
        return scoped(id);
    }

    /** Pending draft revisions keyed by the id of the published card they belong to. */
    public Map<Long, OrgFunction> draftRevisionsOf(Collection<OrgFunction> functions) {
        List<Long> ids = functions.stream().filter(function -> !function.isRevision()).map(OrgFunction::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return orgFunctionRepository.findAllByRevisionOfIdIn(ids).stream()
                .collect(Collectors.toMap(OrgFunction::getRevisionOfId, Function.identity(), (first, second) -> first));
    }

    public List<OrgFunction> pendingReview() {
        return access.global()
                ? orgFunctionRepository.findAllByStatus(PENDING_REVIEW)
                : orgFunctionRepository.findAllByStatusAndOrganizationIdIn(PENDING_REVIEW, access.principal().organizationIds());
    }

    public List<AuditLogResponse> history(Long id) {
        scoped(id);
        boolean global = access.global();
        return auditLogs.findFunctionHistory(id).stream()
                .map(log -> global || log.getAction() != AuditAction.IMPORT
                        ? AuditLogResponse.from(log)
                        : new AuditLogResponse(log.getId(), log.getPerformedByUserId(), log.getPerformedBy(),
                                log.getAction(), log.getPerformedAt(), "Imported this function as DRAFT"))
                .toList();
    }

    @Transactional
    public OrgFunction create(CreateOrgFunctionRequest request) {
        OrgFunction function = newDraft(request);
        orgFunctionRepository.saveAndFlush(function);
        audit.function(function, AuditAction.CREATE, "Draft created");
        translations.enqueue(TranslationTarget.FUNCTION, function.getId());
        return function;
    }

    public OrgFunction newDraft(CreateOrgFunctionRequest request) {
        access.requireOrganization(request.organizationId());
        if (request.organizationId() != null) {
            organizations.requireExisting(request.organizationId());
        }
        OrgFunction function = OrgFunction.builder()
                .name(request.name())
                .description(request.description())
                .organizationId(request.organizationId())
                .requirements(request.requirements())
                .functionCategory(categories.resolve(request.categoryId(), request.category()))
                .sourceLanguage(request.sourceLanguage() == null
                        ? DEFAULT_SOURCE_LANGUAGE
                        : request.sourceLanguage().toLowerCase(Locale.ROOT))
                .officialSourceUrl(OfficialSourceUrl.normalize(request.officialSourceUrl()))
                .status(DRAFT)
                .build();
        applyInstructions(function, request.instructions());
        applyDocumentDetails(function, request.documentDetails());
        return function;
    }

    /**
     * Opens (or returns the existing) draft revision of a published card. The published row is never changed here:
     * visitors keep seeing it until the revision is reviewed and published.
     */
    @Transactional
    public OrgFunction createRevision(Long id) {
        OrgFunction published = scoped(id);
        if (published.isRevision()) {
            throw new WorkflowConflictException("This card is already a draft revision; edit it directly");
        }
        if (published.getStatus() != PUBLISHED) {
            throw new WorkflowConflictException("Only PUBLISHED cards are edited through a revision; found " + published.getStatus());
        }
        Optional<OrgFunction> existing = orgFunctionRepository.findByRevisionOfId(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        OrgFunction revision = new OrgFunction();
        revision.copyContentFrom(published);
        revision.setStatus(DRAFT);
        revision.setRevisionOfId(published.getId());
        orgFunctionRepository.saveAndFlush(revision);
        audit.revision(published, revision, AuditAction.REVISION_CREATE,
                "Draft revision #" + revision.getId() + " opened for published card #" + published.getId() + "; public content unchanged");
        return revision;
    }

    /** Drops the pending draft revision of a published card; the published content is untouched. */
    @Transactional
    public void discardRevision(Long publishedId) {
        OrgFunction published = scoped(publishedId);
        OrgFunction revision = orgFunctionRepository.findByRevisionOfId(published.getId())
                .orElseThrow(() -> new NoSuchElementException("Card " + publishedId + " has no draft revision"));
        access.requireOrganization(revision.getOrganizationId());
        if (revision.getStatus() != DRAFT) {
            throw new WorkflowConflictException("Withdraw the revision from review before discarding it");
        }
        removeRevisionRow(revision, published);
        audit.function(published, AuditAction.REVISION_DISCARD,
                "Draft revision #" + revision.getId() + " discarded; public content unchanged");
    }

    @Transactional
    public OrgFunction update(Long id, UpdateOrgFunctionRequest request) {
        OrgFunction function = scoped(id);
        requireEditable(function);
        List<String> changed = new ArrayList<>();

        boolean nameChanged = request.name() != null && !request.name().equals(function.getName());
        boolean descriptionChanged = request.description() != null && !request.description().equals(function.getDescription());
        if (nameChanged) {
            function.setName(request.name());
            changed.add("name");
        }
        if (descriptionChanged) {
            function.setDescription(request.description());
            changed.add("description");
        }
        if (request.organizationId() != null && !request.organizationId().equals(function.getOrganizationId())) {
            access.requireOrganization(request.organizationId());
            organizations.requireExisting(request.organizationId());
            function.setOrganizationId(request.organizationId());
            if (!function.isRevision()) {
                reports.reassignFunctionReports(function.getId(), request.organizationId());
            }
            changed.add("organizationId");
        }
        if (request.requirements() != null && !request.requirements().equals(function.getRequirements())) {
            function.setRequirements(request.requirements());
            changed.add("requirements");
        }
        Set<InstructionField> instructionsChanged = applyInstructions(function, request.instructions());
        instructionsChanged.forEach(field -> changed.add(field.key()));
        if (applyDocumentDetails(function, request.documentDetails())) {
            changed.add("documentDetails");
        }
        if (request.officialSourceUrl() != null) {
            String officialSourceUrl = OfficialSourceUrl.normalize(request.officialSourceUrl());
            if (!Objects.equals(officialSourceUrl, function.getOfficialSourceUrl())) {
                function.setOfficialSourceUrl(officialSourceUrl);
                changed.add("officialSourceUrl");
            }
        }
        if (request.categoryId() != null || request.category() != null) {
            changeCategory(function, categories.resolve(request.categoryId(), request.category()));
        }
        boolean languageChanged = request.sourceLanguage() != null
                && !request.sourceLanguage().equalsIgnoreCase(function.getSourceLanguage());
        if (languageChanged) {
            function.setSourceLanguage(request.sourceLanguage().toLowerCase(Locale.ROOT));
            changed.add("sourceLanguage");
        }

        translationService.invalidateMachineTranslations(function,
                nameChanged || languageChanged, descriptionChanged || languageChanged);
        translationService.invalidateInstructionTranslations(function, instructionsChanged, languageChanged);
        if (nameChanged || languageChanged) {
            function.setNameTranslations(
                    withSourceMirror(function.getNameTranslations(), function.getSourceLanguage(), function.getName()));
        }
        if (descriptionChanged || languageChanged) {
            function.setDescriptionTranslations(
                    withSourceMirror(function.getDescriptionTranslations(), function.getSourceLanguage(), function.getDescription()));
        }
        if (!changed.isEmpty()) {
            recordChange(function, changed);
        }
        OrgFunction saved = orgFunctionRepository.saveAndFlush(function);
        if (nameChanged || descriptionChanged || languageChanged || !instructionsChanged.isEmpty()) {
            translations.enqueue(TranslationTarget.FUNCTION, saved.getId());
        }
        return saved;
    }

    @Transactional
    public OrgFunction updateRequirements(Long id, String requirements) {
        OrgFunction function = scoped(id);
        requireEditable(function);
        if (!Objects.equals(requirements, function.getRequirements())) {
            function.setRequirements(requirements);
            recordChange(function, List.of("requirements"));
        }
        return orgFunctionRepository.saveAndFlush(function);
    }

    @Transactional
    public OrgFunction updateTranslations(Long id, FunctionTranslationsRequest request) {
        access.requireTranslationEditor();
        OrgFunction function = scoped(id);
        requireEditable(function);
        if (request.nameTranslations() == null && request.descriptionTranslations() == null) {
            throw new IllegalArgumentException("At least one translation field is required");
        }
        List<String> changed = new ArrayList<>();
        if (request.nameTranslations() != null) {
            Map<String, TranslatedText> values = TranslatedText.humanEdits(request.nameTranslations());
            if (!values.equals(function.getNameTranslations())) {
                function.setNameTranslations(values);
                changed.add("nameTranslations");
            }
        }
        if (request.descriptionTranslations() != null) {
            Map<String, TranslatedText> values = TranslatedText.humanEdits(request.descriptionTranslations());
            if (!values.equals(function.getDescriptionTranslations())) {
                function.setDescriptionTranslations(values);
                changed.add("descriptionTranslations");
            }
        }
        if (!changed.isEmpty()) {
            audit.function(function, AuditAction.TRANSLATION_EDIT, "Replaced fields: " + String.join(", ", changed));
        }
        return orgFunctionRepository.saveAndFlush(function);
    }

    @Transactional
    public OrgFunction updateLanguageTranslation(Long id, String language, LanguageTranslationRequest request) {
        access.requireTranslationEditor();
        OrgFunction function = scoped(id);
        requireEditable(function);
        String code = language.toLowerCase(Locale.ROOT);
        if (code.length() > LANGUAGE_CODE_MAX_LENGTH || !LANGUAGE_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid language code");
        }
        if (code.equals(function.getSourceLanguage())) {
            throw new IllegalArgumentException("Edit original-language text through the main card editor");
        }
        Map<String, TranslatedText> names = new LinkedHashMap<>(function.getNameTranslations());
        Map<String, TranslatedText> descriptions = new LinkedHashMap<>(function.getDescriptionTranslations());
        names.put(code, new TranslatedText(request.name(), TranslatedText.HUMAN));
        if (request.description() != null) {
            descriptions.put(code, new TranslatedText(request.description(), TranslatedText.HUMAN));
        }
        Map<String, Map<String, TranslatedText>> instructions = translatedInstructions(function, code, request.instructions());
        if (!names.equals(function.getNameTranslations()) || !descriptions.equals(function.getDescriptionTranslations())
                || !instructions.equals(currentInstructionTranslations(function))) {
            function.setNameTranslations(names);
            function.setDescriptionTranslations(descriptions);
            function.setInstructionTranslations(instructions);
            audit.function(function, AuditAction.TRANSLATION_EDIT, "Manual translation: " + code);
        }
        return orgFunctionRepository.saveAndFlush(function);
    }

    /** Drafts and deactivated cards enter review directly; nothing becomes public without a reviewer publishing it. */
    @Transactional
    public OrgFunction submitForReview(Long id) {
        return transition(id, EnumSet.of(DRAFT, DEACTIVATED), PENDING_REVIEW, AuditAction.SUBMIT_REVIEW, null);
    }

    /** The submitting side takes a card back from the review queue without a reviewer. */
    @Transactional
    public OrgFunction withdraw(Long id) {
        return transition(id, EnumSet.of(PENDING_REVIEW), DRAFT, AuditAction.WITHDRAW, null);
    }

    @Transactional
    public OrgFunction reject(Long id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > REJECTION_REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("A rejection reason of 1-2000 characters is required");
        }
        return transition(id, EnumSet.of(PENDING_REVIEW), DRAFT, AuditAction.REJECT, reason);
    }

    /**
     * Publishes a reviewed card. A reviewed draft revision is merged into its published card, which keeps its id
     * and history, and the revision row disappears; the public version changes only at this moment.
     */
    @Transactional
    public OrgFunction publish(Long id) {
        OrgFunction function = scoped(id);
        if (function.getStatus() != PENDING_REVIEW) {
            throw new WorkflowConflictException(
                    "Expected PENDING_REVIEW, found " + function.getStatus() + "; cannot transition to PUBLISHED");
        }
        if (!function.isRevision()) {
            FunctionStatus previousPublicStatus = previousPublicStatus(function);
            OrgFunction published = transition(id, EnumSet.of(PENDING_REVIEW), PUBLISHED, AuditAction.PUBLISH, null);
            publicChanges.recordPublication(published, previousPublicStatus, java.time.Instant.now());
            return published;
        }
        organizations.requireExisting(function.getOrganizationId());
        OrgFunction published = orgFunctionRepository.findById(function.getRevisionOfId())
                .orElseThrow(() -> new WorkflowConflictException("The published card of this revision no longer exists"));
        access.requireOrganization(published.getOrganizationId());
        if (published.getStatus() != PUBLISHED && published.getStatus() != DEACTIVATED) {
            throw new WorkflowConflictException("The published card is " + published.getStatus() + "; the revision cannot replace it");
        }
        Long previousOrganization = published.getOrganizationId();
        FunctionStatus previousStatus = published.getStatus();
        published.copyContentFrom(function);
        published.setStatus(PUBLISHED);
        orgFunctionRepository.saveAndFlush(published);
        if (!Objects.equals(previousOrganization, published.getOrganizationId())) {
            reports.reassignFunctionReports(published.getId(), published.getOrganizationId());
        }
        removeRevisionRow(function, published);
        audit.function(published, AuditAction.PUBLISH,
                "Revision #" + function.getId() + " approved: public content replaced (" + previousStatus + " -> PUBLISHED)");
        publicChanges.recordPublication(published, previousStatus, java.time.Instant.now());
        return published;
    }

    /** A card re-entering publication after deactivation is reported as republished rather than updated. */
    private FunctionStatus previousPublicStatus(OrgFunction published) {
        boolean wasDeactivated = auditLogs.findFunctionHistory(published.getId()).stream()
                .map(log -> log.getAction())
                .filter(action -> action == AuditAction.DEACTIVATE || action == AuditAction.REACTIVATE || action == AuditAction.PUBLISH)
                .reduce((first, second) -> second)
                .map(action -> action == AuditAction.DEACTIVATE || action == AuditAction.REACTIVATE)
                .orElse(false);
        return wasDeactivated ? DEACTIVATED : PENDING_REVIEW;
    }

    @Transactional
    public OrgFunction reactivate(Long id) {
        return transition(id, EnumSet.of(DEACTIVATED), PENDING_REVIEW, AuditAction.REACTIVATE, null);
    }

    @Transactional
    public void deactivate(Long id) {
        transition(id, EnumSet.of(PUBLISHED), DEACTIVATED, AuditAction.DEACTIVATE, null);
    }

    private OrgFunction transition(Long id, Set<FunctionStatus> allowed, FunctionStatus to, AuditAction action, String reason) {
        OrgFunction function = scoped(id);
        FunctionStatus from = function.getStatus();
        if (!allowed.contains(from)) {
            throw new WorkflowConflictException(
                    "Expected " + describe(allowed) + ", found " + from + "; cannot transition to " + to);
        }
        if (to == PENDING_REVIEW || to == PUBLISHED) {
            organizations.requireExisting(function.getOrganizationId());
        }
        function.setStatus(to);
        orgFunctionRepository.saveAndFlush(function);
        audit.function(function, action, reason == null ? from + " -> " + to : reason);
        if (to == DRAFT) {
            translations.resume(TranslationTarget.FUNCTION, function.getId());
        }
        return function;
    }

    /** Moves the revision's history onto the published card and deletes the revision row (same transaction). */
    private void removeRevisionRow(OrgFunction revision, OrgFunction published) {
        auditLogs.flush();
        auditLogs.copyFunctionLinks(revision.getId(), published.getId());
        auditLogs.deleteFunctionLinks(revision.getId());
        reminders.deleteAllByFunctionId(revision.getId());
        translations.remove(TranslationTarget.FUNCTION, revision.getId());
        orgFunctionRepository.delete(revision);
        orgFunctionRepository.flush();
    }

    private static String describe(Set<FunctionStatus> statuses) {
        return statuses.stream().map(FunctionStatus::name).collect(Collectors.joining(" or "));
    }

    private void changeCategory(OrgFunction function, FunctionCategory category) {
        Long previousId = function.getFunctionCategory() == null ? null : function.getFunctionCategory().getId();
        Long nextId = category == null ? null : category.getId();
        if (!Objects.equals(previousId, nextId)) {
            function.setFunctionCategory(category);
            audit.function(function, AuditAction.CATEGORY_CHANGE, "categoryId: " + previousId + " -> " + nextId);
        }
    }

    private Set<InstructionField> applyInstructions(OrgFunction function, ServiceInstructions instructions) {
        Set<InstructionField> changed = EnumSet.noneOf(InstructionField.class);
        if (instructions == null) {
            return changed;
        }
        for (InstructionField field : InstructionField.values()) {
            String value = InstructionText.normalize(field, instructions.value(field));
            if (!Objects.equals(value, field.get(function))) {
                field.set(function, value);
                changed.add(field);
            }
        }
        return changed;
    }

    /**
     * Replaces the structured document details ({@code requested} not null) or, when only the document list changed,
     * drops details whose line no longer exists. Each detail must name a current line of "Required documents"; empty
     * details are not stored, so nothing is shown that the organization did not provide.
     *
     * @return whether the stored details changed
     */
    private boolean applyDocumentDetails(OrgFunction function, List<DocumentDetail> requested) {
        List<String> lines = InstructionText.lines(function.getRequiredDocuments());
        List<DocumentDetail> next = new ArrayList<>();
        if (requested == null) {
            function.documentDetailsOrEmpty().stream().filter(detail -> lines.contains(detail.document())).forEach(next::add);
        } else {
            Set<String> seen = new java.util.HashSet<>();
            for (DocumentDetail raw : requested) {
                if (raw == null) {
                    continue;
                }
                DocumentDetail detail = DocumentDetails.normalize(raw);
                if (!lines.contains(detail.document())) {
                    throw new InvalidFieldException("documentDetails",
                            "Each document detail must belong to a line of Required documents: " + abbreviate(detail.document()));
                }
                if (!seen.add(detail.document())) {
                    throw new InvalidFieldException("documentDetails", "A document has more than one set of details: " + abbreviate(detail.document()));
                }
                if (!detail.empty()) {
                    next.add(detail);
                }
            }
            // Keep the order of the document list.
            next.sort(java.util.Comparator.comparingInt(detail -> lines.indexOf(detail.document())));
        }
        List<DocumentDetail> stored = next.isEmpty() ? null : next;
        if (Objects.equals(stored, function.getDocumentDetails() == null || function.getDocumentDetails().isEmpty() ? null : function.getDocumentDetails())) {
            return false;
        }
        function.setDocumentDetails(stored);
        return true;
    }

    private static String abbreviate(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80) + "…";
    }

    private void recordChange(OrgFunction function, List<String> changed) {
        String details = "Changed fields: " + String.join(", ", changed);
        boolean verifiedContent = changed.stream()
                .anyMatch(field -> VERIFIED_FIELDS.contains(field) || InstructionField.byKey(field).isPresent());
        if (verifiedContent && function.markVerificationOutdated()) {
            details += "; verification outdated";
        }
        audit.function(function, AuditAction.UPDATE, details);
    }

    private static Map<String, Map<String, TranslatedText>> currentInstructionTranslations(OrgFunction function) {
        return function.getInstructionTranslations() == null ? Map.of() : function.getInstructionTranslations();
    }

    private static Map<String, Map<String, TranslatedText>> translatedInstructions(OrgFunction function, String code,
                                                                                   Map<String, String> values) {
        Map<String, Map<String, TranslatedText>> result = new LinkedHashMap<>();
        currentInstructionTranslations(function).forEach((key, translations) -> result.put(key, new LinkedHashMap<>(translations)));
        if (values == null) {
            return result;
        }
        values.forEach((key, raw) -> {
            InstructionField field = InstructionField.byKey(key)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown instruction field: " + key));
            String text = InstructionText.normalize(field, raw);
            Map<String, TranslatedText> translations = result.computeIfAbsent(field.key(), ignored -> new LinkedHashMap<>());
            if (text == null) {
                translations.remove(code);
            } else if (field.get(function) == null) {
                throw new IllegalArgumentException(field.key() + ": add the original text before translating it");
            } else {
                translations.put(code, new TranslatedText(text, TranslatedText.HUMAN));
            }
            if (translations.isEmpty()) {
                result.remove(field.key());
            }
        });
        return result;
    }

    private OrgFunction scoped(Long id) {
        OrgFunction function = orgFunctionRepository.findById(id).orElseThrow(() -> notFound(id));
        access.requireOrganization(function.getOrganizationId());
        return function;
    }

    private static void requireEditable(OrgFunction function) {
        if (function.getStatus() == PUBLISHED) {
            throw new WorkflowConflictException(
                    "Published cards are edited through a draft revision (POST /api/functions/{id}/revisions); the public version is never changed in place");
        }
        if (!function.isEditable()) {
            throw new WorkflowConflictException(
                    "Editing requires DRAFT or DEACTIVATED; a card under review must be withdrawn or returned to draft first");
        }
    }

    private static Map<String, TranslatedText> withSourceMirror(Map<String, TranslatedText> values, String language, String text) {
        Map<String, TranslatedText> result = new LinkedHashMap<>(values);
        if (text != null) {
            result.put(language, new TranslatedText(text, TranslatedText.HUMAN));
        }
        return result;
    }

    private static NoSuchElementException notFound(Long id) {
        return new NoSuchElementException("Function not found, ID: " + id);
    }
}
