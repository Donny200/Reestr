package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** "Section reviewed" mark of the reviewer checklist for one review cycle. */
@Entity @Table(name = "function_review_marks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FunctionReviewMark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long functionId;
    @Column(nullable = false)
    private int reviewCycle;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ReviewSection section;
    private Long reviewerUserId;
    @Column(nullable = false, length = 320)
    private String reviewer;
    @Column(nullable = false)
    private Instant reviewedAt;
}
