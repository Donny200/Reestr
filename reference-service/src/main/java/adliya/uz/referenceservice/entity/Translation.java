package adliya.uz.referenceservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "translations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_translations_language_key",
                columnNames = {"language_code", "translation_key_id"}
        ),
        indexes = @Index(name = "idx_translations_language_code", columnList = "language_code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "translation_key_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_translations_translation_key")
    )
    private TranslationKey translationKey;

    @Column(name = "language_code", nullable = false, length = 64)
    private String languageCode;

    @Column(name = "translation_value", nullable = false, columnDefinition = "TEXT")
    private String translationValue;

    /** Null only for rows written before provenance existed; the catalog synchronizer classifies them on start-up. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TranslationSource source;

    /** Hash of the English text this value was written for; a different current hash marks the value as outdated. */
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
