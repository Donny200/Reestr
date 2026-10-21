package adliya.uz.referenceservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row per interface language: the state of filling its missing dictionary entries by machine translation. */
@Entity
@Table(name = "interface_translation_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterfaceTranslationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "language_code", nullable = false, length = 64, unique = true)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterfaceTranslationJobStatus status;

    @Column(name = "provider_code", length = 32)
    private String providerCode;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "missing_keys", nullable = false)
    @Builder.Default
    private int missingKeys = 0;

    @Column(name = "translated_keys", nullable = false)
    @Builder.Default
    private int translatedKeys = 0;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    private Long version;
}
