package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Scheduling state of the background machine translation of one card or category. The content itself stays on the
 * target record; this row only says whether the worker still has to look at it.
 */
@Entity
@Table(name = "translation_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TranslationJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TranslationTarget targetType;
    @Column(nullable = false)
    private Long targetId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TranslationJobStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant requestedAt;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private Instant finishedAt;
    @Column(length = 500)
    private String lastError;

    public boolean isOpen() {
        return status == TranslationJobStatus.PENDING || status == TranslationJobStatus.RUNNING;
    }
}
