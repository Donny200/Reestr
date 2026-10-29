package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OrgFunctionResponse(
        Long id, String name, String description, Long organizationId, String requirements,
        String category, Boolean active,
        Map<String, TranslatedText> nameTranslations, Map<String, TranslatedText> descriptionTranslations,
        FunctionStatus status, Long categoryId, String sourceLanguage,
        ServiceInstructions instructions, Map<String, Map<String, TranslatedText>> instructionTranslations,
        /** Structured details of required documents; empty when the organization provided none. */
        List<DocumentDetail> documentDetails,
        String officialSourceUrl, Instant lastVerifiedAt, VerificationStatus verificationStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long verifiedByUserId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long revisionOfId,
        @JsonInclude(JsonInclude.Include.NON_NULL) RevisionSummary revision,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant lastUpdatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) AssigneeSummary assignee,
        /** Background translation state; staff responses only. */
        @JsonInclude(JsonInclude.Include.NON_NULL) MachineTranslationSummary machineTranslation,
        /** Digitalization, decentralization and risk profile; staff responses only. */
        @JsonInclude(JsonInclude.Include.NON_NULL) ServiceProfile profile,
        /** Editor step the author last saved from; staff responses only. */
        @JsonInclude(JsonInclude.Include.NON_NULL) String editorStep,
        /** Review cycle and open corrections; staff responses only. */
        @JsonInclude(JsonInclude.Include.NON_NULL) ReviewSummary review
) {
    public OrgFunctionResponse withMachineTranslation(MachineTranslationSummary summary) {
        return new OrgFunctionResponse(id, name, description, organizationId, requirements, category, active,
                nameTranslations, descriptionTranslations, status, categoryId, sourceLanguage, instructions,
                instructionTranslations, documentDetails, officialSourceUrl, lastVerifiedAt, verificationStatus, verifiedByUserId,
                revisionOfId, revision, lastUpdatedAt, assignee, summary, profile, editorStep, review);
    }

    /** Adds the number of open corrections to a staff response. */
    public OrgFunctionResponse withOpenCorrections(long openCorrections) {
        ReviewSummary summary = new ReviewSummary(review == null ? 0 : review.cycle(), openCorrections);
        return new OrgFunctionResponse(id, name, description, organizationId, requirements, category, active,
                nameTranslations, descriptionTranslations, status, categoryId, sourceLanguage, instructions,
                instructionTranslations, documentDetails, officialSourceUrl, lastVerifiedAt, verificationStatus, verifiedByUserId,
                revisionOfId, revision, lastUpdatedAt, assignee, machineTranslation, profile, editorStep, summary);
    }

    /** Current review cycle (number of submissions) and open field-level corrections, as seen by staff. */
    public record ReviewSummary(int cycle, @JsonInclude(JsonInclude.Include.NON_NULL) Long openCorrections) {}

    /** Pending draft revision of a published card, as seen by staff. */
    public record RevisionSummary(Long id, FunctionStatus status) {
        public static RevisionSummary of(OrgFunction revision) {
            return revision == null ? null : new RevisionSummary(revision.getId(), revision.getStatus());
        }
    }

    public static OrgFunctionResponse from(OrgFunction function) {
        return of(function, function.getVerifiedByUserId(), function.getRevisionOfId(), null, null, AssigneeSummary.of(function), true);
    }

    public static OrgFunctionResponse from(OrgFunction function, OrgFunction draftRevision) {
        return of(function, function.getVerifiedByUserId(), function.getRevisionOfId(), RevisionSummary.of(draftRevision), null,
                AssigneeSummary.of(function), true);
    }

    /** Public view: no staff identifiers, no revision details, no assignee, no staff-only profile or review state. */
    public static OrgFunctionResponse publicFrom(OrgFunction function) {
        return of(function, null, null, null, null, null, false);
    }

    public static OrgFunctionResponse publicFrom(OrgFunction function, Instant lastUpdatedAt) {
        return of(function, null, null, null, lastUpdatedAt, null, false);
    }

    private static OrgFunctionResponse of(OrgFunction function, Long verifiedByUserId, Long revisionOfId, RevisionSummary revision,
                                          Instant lastUpdatedAt, AssigneeSummary assignee, boolean staff) {
        return new OrgFunctionResponse(function.getId(), function.getName(), function.getDescription(),
                function.getOrganizationId(), function.getRequirements(), function.getCategory(), function.getActive(),
                function.getNameTranslations(), function.getDescriptionTranslations(), function.getStatus(),
                function.getFunctionCategory() == null ? null : function.getFunctionCategory().getId(), function.getSourceLanguage(),
                ServiceInstructions.of(function),
                function.getInstructionTranslations() == null ? Map.of() : function.getInstructionTranslations(),
                function.documentDetailsOrEmpty(), function.getOfficialSourceUrl(), function.getLastVerifiedAt(),
                function.verificationStatus(Instant.now()), verifiedByUserId, revisionOfId, revision, lastUpdatedAt, assignee, null,
                staff ? ServiceProfile.of(function) : null, staff ? function.getEditorStep() : null,
                staff ? new ReviewSummary(function.getReviewCycle(), null) : null);
    }
}
