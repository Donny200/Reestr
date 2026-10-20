package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "engagement_daily_counts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EngagementDailyCount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDate activityDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private EngagementEventType eventType;
    private Long organizationId;
    private Long functionId;
    @Column(nullable = false)
    private long eventCount;
}
