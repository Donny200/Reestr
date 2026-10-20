package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "function_categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FunctionCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, TranslatedText> nameTranslations = new LinkedHashMap<>();
    /** Language of {@code name} as entered by staff; null means the catalog default (en). */
    @Column(length = 35)
    private String sourceLanguage;
    @Version
    private Long version;
}
