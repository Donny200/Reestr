package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.FunctionReviewResponse;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.FunctionReviewComment;
import adliya.uz.functioncatalogservice.entity.FunctionReviewMark;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ReviewCommentStatus;
import adliya.uz.functioncatalogservice.entity.ReviewResolution;
import adliya.uz.functioncatalogservice.entity.ReviewSection;
import adliya.uz.functioncatalogservice.entity.TranslationTarget;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.FunctionReviewCommentRepository;
import adliya.uz.functioncatalogservice.repository.FunctionReviewMarkRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static adliya.uz.functioncatalogservice.entity.FunctionStatus.CHANGES_REQUESTED;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.PENDING_REVIEW;
import static adliya.uz.functioncatalogservice.entity.FunctionStatus.PUBLISHED;

/**
 * Field-level review of a submitted card. A reviewer attaches correction notes to stable field paths (see
 * {@link ReviewFields}) while the card is PENDING_REVIEW and then either requests changes (CHANGES_REQUESTED, editable)
 * or approves it through the existing publish action, which is refused while any note is open. A note stays open
 * until the author changes its field and resubmits, or a reviewer resolves it; resolved notes remain as history.
 */
@Service
@RequiredArgsConstructor
public class FunctionReviewService {

    private static final int SUMMARY_MAX_LENGTH = 2000;

    private final OrgFunctionRepository functions;
    private final FunctionReviewCommentRepository comments;
    private final FunctionReviewMarkRepository marks;
    private final CatalogAccess access;
    private final AuditWriter audit;
    private final MachineTranslationQueue translations;

    @Transactional(readOnly = true)
    public FunctionReviewResponse review(Long id) {
        OrgFunction function = scoped(id);
        List<Long> ids = function.isRevision() ? List.of(function.getId(), function.getRevisionOfId()) : List.of(function.getId());
        List<FunctionReviewResponse.Comment> all = comments.findAllByFunctionIdInOrderByCreatedAtAscIdAsc(ids).stream()
                .map(comment -> FunctionReviewResponse.Comment.of(comment, comment.isOpen() && changed(function, comment)))
                .toList();
        List<String> unaddressed = unaddressed(function).stream().map(FunctionReviewComment::getFieldPath).toList();
        List<FunctionReviewResponse.Mark> reviewed = marks.findAllByFunctionIdAndReviewCycleOrderBySectionAsc(function.getId(),
                function.getReviewCycle()).stream().map(FunctionReviewResponse.Mark::of).toList();
        return new FunctionReviewResponse(function.getId(), function.getStatus(), function.getReviewCycle(), all, reviewed,
                unaddressed, function.isEditable() && unaddressed.isEmpty());
    }

    /** Flags one field or section of a submission under review; one open note per field. */
    @Transactional
    public FunctionReviewResponse addComment(Long id, String fieldPath, String text) {
        OrgFunction function = locked(id);
        requireUnderReview(function);
        String comment = text == null ? "" : text.strip();
        if (comment.isEmpty() || comment.length() > SUMMARY_MAX_LENGTH) {
            throw new InvalidFieldException("comment", "An explanation of 1-2000 characters is required");
        }
        ReviewSection section = ReviewFields.sectionOf(fieldPath, function)
                .orElseThrow(() -> new InvalidFieldException("fieldPath", "Unknown or removed field: " + fieldPath));
        if (comments.existsByFunctionIdAndFieldPathAndStatus(function.getId(), fieldPath, ReviewCommentStatus.OPEN)) {
            throw new WorkflowConflictException("This field already has an open correction; resolve it before adding another");
        }
        AuditWriter.Actor actor = audit.actor();
        FunctionReviewComment saved = comments.saveAndFlush(FunctionReviewComment.builder()
                .functionId(function.getId()).fieldPath(fieldPath).section(section).reviewCycle(function.getReviewCycle())
                .comment(comment).valueHash(ReviewFields.fingerprint(function, fieldPath)).status(ReviewCommentStatus.OPEN)
                .reviewerUserId(actor.id()).reviewer(actor.email()).createdAt(Instant.now()).build());
        audit.function(function, AuditAction.REVIEW_COMMENT_ADD,
                "Correction #" + saved.getId() + " on " + fieldPath + " (cycle " + function.getReviewCycle() + "): " + comment);
        return review(function.getId());
    }

    /** A reviewer closes a note explicitly (e.g. the author's text is correct after all); the note stays as history. */
    @Transactional
    public FunctionReviewResponse resolveComment(Long id, Long commentId) {
        OrgFunction function = locked(id);
        if (function.getStatus() == PUBLISHED) {
            throw new WorkflowConflictException("Published cards have no open corrections");
        }
        FunctionReviewComment comment = comments.findById(commentId)
                .filter(candidate -> candidate.getFunctionId().equals(function.getId()))
                .orElseThrow(() -> new NoSuchElementException("Review note " + commentId + " not found on card " + id));
        if (!comment.isOpen()) {
            throw new WorkflowConflictException("This correction is already resolved");
        }
        AuditWriter.Actor actor = audit.actor();
        comment.resolve(ReviewResolution.REVIEWER_RESOLVED, actor.id(), actor.email(), function.getReviewCycle(), Instant.now());
        comments.saveAndFlush(comment);
        audit.function(function, AuditAction.REVIEW_COMMENT_RESOLVE, "Correction #" + comment.getId() + " on " + comment.getFieldPath()
                + " resolved by reviewer");
        return review(function.getId());
    }

    @Transactional
    public FunctionReviewResponse markSection(Long id, String sectionKey, boolean reviewed) {
        OrgFunction function = locked(id);
        requireUnderReview(function);
        ReviewSection section = ReviewSection.byKey(sectionKey)
                .orElseThrow(() -> new InvalidFieldException("section", "Unknown section: " + sectionKey));
        var existing = marks.findByFunctionIdAndReviewCycleAndSection(function.getId(), function.getReviewCycle(), section);
        if (reviewed && existing.isEmpty()) {
            AuditWriter.Actor actor = audit.actor();
            marks.saveAndFlush(FunctionReviewMark.builder().functionId(function.getId()).reviewCycle(function.getReviewCycle())
                    .section(section).reviewerUserId(actor.id()).reviewer(actor.email()).reviewedAt(Instant.now()).build());
        } else if (!reviewed) {
            existing.ifPresent(marks::delete);
            marks.flush();
        }
        return review(function.getId());
    }

    /**
     * Returns the submission to its author with the open notes. The status change and its audit entry commit together;
     * a concurrent approval or second request is refused because the row is locked and no longer PENDING_REVIEW.
     */
    @Transactional
    public OrgFunction requestChanges(Long id, String summary) {
        OrgFunction function = locked(id);
        requireUnderReview(function);
        String note = summary == null ? null : summary.strip();
        if (note != null && note.length() > SUMMARY_MAX_LENGTH) {
            throw new InvalidFieldException("summary", "The overall note can have at most 2000 characters");
        }
        List<FunctionReviewComment> open = comments.findAllByFunctionIdAndStatusOrderByIdAsc(function.getId(), ReviewCommentStatus.OPEN);
        if (open.isEmpty()) {
            throw new WorkflowConflictException("Flag at least one field that needs a correction before requesting changes");
        }
        function.setStatus(CHANGES_REQUESTED);
        functions.saveAndFlush(function);
        String details = "PENDING_REVIEW -> CHANGES_REQUESTED; cycle " + function.getReviewCycle() + "; corrections: "
                + open.stream().map(FunctionReviewComment::getFieldPath).collect(Collectors.joining(", "));
        audit.function(function, AuditAction.REQUEST_CHANGES, note == null || note.isEmpty() ? details : details + "\n" + note);
        translations.resume(TranslationTarget.FUNCTION, function.getId());
        return function;
    }

    /** Approval guard: a card with open corrections cannot be published. Called inside the publish transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireNoOpenCorrections(OrgFunction function) {
        List<FunctionReviewComment> open = comments.findAllByFunctionIdAndStatusOrderByIdAsc(function.getId(), ReviewCommentStatus.OPEN);
        if (!open.isEmpty()) {
            throw new WorkflowConflictException("Resolve the open corrections before approving: "
                    + open.stream().map(FunctionReviewComment::getFieldPath).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Resubmission rule: every open note must have its field changed since the review. Those notes are closed as
     * ADDRESSED in the submission transaction and stay in the history; the caller then starts the next cycle.
     *
     * @return the paths of the notes closed by this submission
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> addressOnSubmit(OrgFunction function) {
        List<FunctionReviewComment> open = comments.findAllByFunctionIdAndStatusOrderByIdAsc(function.getId(), ReviewCommentStatus.OPEN);
        List<String> unaddressed = open.stream().filter(comment -> !changed(function, comment)).map(FunctionReviewComment::getFieldPath).toList();
        if (!unaddressed.isEmpty()) {
            throw new WorkflowConflictException("Change the fields the reviewer flagged before resubmitting: " + String.join(", ", unaddressed));
        }
        AuditWriter.Actor actor = audit.actor();
        Instant now = Instant.now();
        List<String> addressed = new ArrayList<>();
        for (FunctionReviewComment comment : open) {
            comment.resolve(ReviewResolution.ADDRESSED, actor.id(), actor.email(), function.getReviewCycle() + 1, now);
            addressed.add(comment.getFieldPath());
        }
        comments.saveAllAndFlush(open);
        return addressed;
    }

    /** Moves the notes and marks of a revision onto its published card; open ones are closed when it was discarded. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void moveToPublished(OrgFunction revision, OrgFunction published, boolean discarded) {
        if (discarded) {
            List<FunctionReviewComment> open = comments.findAllByFunctionIdAndStatusOrderByIdAsc(revision.getId(), ReviewCommentStatus.OPEN);
            if (!open.isEmpty()) {
                AuditWriter.Actor actor = audit.actor();
                Instant now = Instant.now();
                open.forEach(comment -> comment.resolve(ReviewResolution.REVISION_DISCARDED, actor.id(), actor.email(),
                        revision.getReviewCycle(), now));
                comments.saveAllAndFlush(open);
            }
        }
        comments.moveAll(revision.getId(), published.getId());
        marks.moveAll(revision.getId(), published.getId());
    }

    /** Open corrections per card, for staff lists. */
    @Transactional(readOnly = true)
    public Map<Long, Long> openCounts(Collection<Long> ids) {
        return comments.openCounts(ids);
    }

    private List<FunctionReviewComment> unaddressed(OrgFunction function) {
        return comments.findAllByFunctionIdAndStatusOrderByIdAsc(function.getId(), ReviewCommentStatus.OPEN).stream()
                .filter(comment -> !changed(function, comment)).toList();
    }

    private static boolean changed(OrgFunction function, FunctionReviewComment comment) {
        return !ReviewFields.fingerprint(function, comment.getFieldPath()).equals(comment.getValueHash());
    }

    private static void requireUnderReview(OrgFunction function) {
        if (function.getStatus() != PENDING_REVIEW) {
            throw new WorkflowConflictException("Review notes are added while the card is PENDING_REVIEW; found " + function.getStatus());
        }
    }

    private OrgFunction scoped(Long id) {
        OrgFunction function = functions.findById(id).orElseThrow(() -> new NoSuchElementException("Function not found, ID: " + id));
        access.requireOrganization(function.getOrganizationId());
        return function;
    }

    /** Row lock: review actions, approval and resubmission of one card are serialized. */
    private OrgFunction locked(Long id) {
        OrgFunction function = functions.findLockedById(id).orElseThrow(() -> new NoSuchElementException("Function not found, ID: " + id));
        access.requireOrganization(function.getOrganizationId());
        return function;
    }
}
