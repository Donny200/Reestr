package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity @Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String entityType;
    private Long entityId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private AuditAction action;
    private Long performedByUserId;
    @Column(nullable = false, length = 320)
    private String performedBy;
    @Column(nullable = false)
    private Instant performedAt;
    @Column(columnDefinition = "text")
    private String details;
    @ElementCollection
    @CollectionTable(name = "audit_log_functions", joinColumns = @JoinColumn(name = "audit_log_id"))
    @Column(name = "function_id")
    @Builder.Default
    private Set<Long> affectedFunctionIds = new LinkedHashSet<>();
}
