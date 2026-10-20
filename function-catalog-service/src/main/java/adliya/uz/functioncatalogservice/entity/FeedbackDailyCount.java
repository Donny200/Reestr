package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Aggregate helpful / not helpful votes for one card and calendar day. No visitor identifiers are stored. */
@Entity
@Table(name = "feedback_daily_counts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeedbackDailyCount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDate activityDate;
    @Column(nullable = false)
    private Long functionId;
    private Long organizationId;
    @Column(nullable = false)
    private long helpfulCount;
    @Column(nullable = false)
    private long notHelpfulCount;
}
