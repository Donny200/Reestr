package adliya.uz.task1.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, TranslatedText> nameTranslations = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, TranslatedText> descriptionTranslations = new LinkedHashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany(mappedBy = "organizations")
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @Column(length = 500)
    private String address;

    @Column(length = 40)
    private String phone;

    @Column(length = 500)
    private String workingHours;

    private Double latitude;

    private Double longitude;

    @Column(length = 500)
    private String mapUrl;

    @Column(length = 20)
    private String regionCode;

    @Column(length = 500)
    private String officialSourceUrl;

    private Instant lastVerifiedAt;

    private Long verifiedByUserId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verificationOutdated = false;

    public VerificationStatus verificationStatus(Instant now) {
        return VerificationStatus.of(lastVerifiedAt, Boolean.TRUE.equals(verificationOutdated), now);
    }
}
