package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "information_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InformationReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReportEntityType entityType;
    @Column(nullable = false)
    private Long entityId;
    @Column(nullable = false, length = 150)
    private String entityLabel;
    @Column(nullable = false)
    private Long organizationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private ReportCategory category;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(length = 254)
    private String contact;
    @Column(length = 35)
    private String language;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.NEW;
    @Column(length = 2000)
    private String resolutionNote;
    private Long handledByUserId;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;
}
