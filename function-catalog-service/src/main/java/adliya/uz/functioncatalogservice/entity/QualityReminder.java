package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "quality_reminders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QualityReminder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long functionId;
    @Column(nullable = false)
    private Long organizationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private QualityIssueType issue;
    @Column(nullable = false)
    private Instant detectedAt;
    @Column(nullable = false)
    private Instant notifiedAt;
    private Instant acknowledgedAt;
    private Long acknowledgedByUserId;
    private Instant resolvedAt;
    /** Responsible staff member of the card when the reminder was last scanned; null when the card is unassigned. */
    private Long assignedUserId;
}
