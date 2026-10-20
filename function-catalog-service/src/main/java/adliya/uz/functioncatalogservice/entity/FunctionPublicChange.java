package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One approved publication of a card, visible to the public. Stores only when it happened, what kind of publication
 * it was and which public sections differ from the previous public version. Section hashes let the next publication
 * compute its diff without keeping any text; they are never exposed.
 */
@Entity
@Table(name = "function_public_changes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FunctionPublicChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long functionId;
    @Column(nullable = false)
    private Instant publishedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PublicChangeType changeType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> sections = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private Map<String, String> sectionHashes;
}
