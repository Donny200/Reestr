package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A reviewer's correction request attached to one field (stable path such as {@code instructions.fee} or
 * {@code documentDetails.<id>}) or to a whole section ({@code section.mechanism}). Rows are never deleted.
 */
@Entity @Table(name = "function_review_comments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FunctionReviewComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long functionId;
    @Column(nullable = false, length = 200)
    private String fieldPath;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ReviewSection section;
    @Column(nullable = false)
    private int reviewCycle;
    @Column(nullable = false, length = 2000)
    private String comment;
    /** SHA-256 of the field value the reviewer saw; a different current value means the author changed it. */
    @Column(nullable = false, length = 64)
    private String valueHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReviewCommentStatus status;
    private Long reviewerUserId;
    @Column(nullable = false, length = 320)
    private String reviewer;
    @Column(nullable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private ReviewResolution resolution;
    private Long resolvedByUserId;
    @Column(length = 320)
    private String resolvedBy;
    private Instant resolvedAt;
    private Integer resolvedInCycle;

    public boolean isOpen() { return status == ReviewCommentStatus.OPEN; }

    public void resolve(ReviewResolution how, Long userId, String by, int cycle, Instant at) {
        status = ReviewCommentStatus.RESOLVED;
        resolution = how;
        resolvedByUserId = userId;
        resolvedBy = by;
        resolvedInCycle = cycle;
        resolvedAt = at;
    }
}
