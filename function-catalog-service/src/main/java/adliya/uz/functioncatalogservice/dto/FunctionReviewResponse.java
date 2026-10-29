package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.FunctionReviewComment;
import adliya.uz.functioncatalogservice.entity.FunctionReviewMark;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.ReviewCommentStatus;
import adliya.uz.functioncatalogservice.entity.ReviewResolution;

import java.time.Instant;
import java.util.List;

/**
 * Review state of a card for staff: the current cycle, every review note (open ones and the resolved history, including
 * that of the published card when this is a draft revision) and the reviewer's section checklist of the current cycle.
 */
public record FunctionReviewResponse(
        Long functionId,
        FunctionStatus status,
        int reviewCycle,
        List<Comment> comments,
        List<Mark> reviewedSections,
        /** Open notes whose field has not changed since the review; resubmission waits for these. */
        List<String> unaddressedFields,
        boolean canResubmit
) {
    public record Comment(
            Long id, Long functionId, String fieldPath, String section, int reviewCycle, String comment,
            ReviewCommentStatus status, Long reviewerUserId, String reviewer, Instant createdAt,
            /** Open note whose field value differs from what the reviewer saw. */
            boolean addressed,
            ReviewResolution resolution, Long resolvedByUserId, String resolvedBy, Instant resolvedAt, Integer resolvedInCycle
    ) {
        public static Comment of(FunctionReviewComment comment, boolean addressed) {
            return new Comment(comment.getId(), comment.getFunctionId(), comment.getFieldPath(), comment.getSection().key(),
                    comment.getReviewCycle(), comment.getComment(), comment.getStatus(), comment.getReviewerUserId(),
                    comment.getReviewer(), comment.getCreatedAt(), addressed, comment.getResolution(),
                    comment.getResolvedByUserId(), comment.getResolvedBy(), comment.getResolvedAt(), comment.getResolvedInCycle());
        }
    }

    public record Mark(String section, Long reviewerUserId, String reviewer, Instant reviewedAt) {
        public static Mark of(FunctionReviewMark mark) {
            return new Mark(mark.getSection().key(), mark.getReviewerUserId(), mark.getReviewer(), mark.getReviewedAt());
        }
    }
}
