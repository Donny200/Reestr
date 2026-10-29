package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "org_functions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrgFunction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 500)
    private String description;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, TranslatedText> nameTranslations = new LinkedHashMap<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, TranslatedText> descriptionTranslations = new LinkedHashMap<>();
    private Long organizationId;
    @Column(length = 500)
    private String requirements;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private FunctionCategory functionCategory;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    @Builder.Default
    private FunctionStatus status = FunctionStatus.DRAFT;
    @Version
    private Long version;
    @Column(unique = true, length = 100)
    private String seedKey;
    @Column(nullable = false, length = 35) @Builder.Default
    private String sourceLanguage = "en";
    @Column(length = 2000)
    private String whoCanUse;
    @Column(length = 4000)
    private String steps;
    @Column(length = 4000)
    private String requiredDocuments;
    @Column(length = 2000)
    private String whereHowToApply;
    @Column(length = 500)
    private String processingTime;
    @Column(length = 500)
    private String fee;
    @Column(length = 500)
    private String officialSourceUrl;
    /** Structured details per line of {@link #requiredDocuments}; null or empty for cards that have none. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private List<DocumentDetail> documentDetails;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Map<String, TranslatedText>> instructionTranslations = new LinkedHashMap<>();
    private Instant lastVerifiedAt;
    private Long verifiedByUserId;
    @Column(nullable = false) @Builder.Default
    private boolean verificationOutdated = false;
    /** Set on a draft revision: the id of the published card whose pending edits this row carries. */
    @Column(name = "revision_of_id")
    private Long revisionOfId;
    /** Staff member responsible for keeping this card current; lives on the main row, never on a revision. */
    private Long assignedUserId;
    @Column(length = 320)
    private String assignedUserEmail;
    @Column(length = 150)
    private String assignedUserName;
    private Instant assignedAt;
    /** Staff-only service profile (digitalization, decentralization and risk); null until the organization fills it in. */
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private DeliveryChannel deliveryChannel;
    @Column(length = 500)
    private String onlineServiceUrl;
    @Enumerated(EnumType.STRING) @Column(length = 30)
    private ProvisionLevel provisionLevel;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private RiskLevel riskLevel;
    @Column(length = 2000)
    private String riskNotes;
    /** Editor step the author last saved from, so a draft resumes where it was left; not content, not audited. */
    @Column(length = 30)
    private String editorStep;
    /** Number of submissions for review; review notes and section marks carry the cycle they belong to. */
    @Column(nullable = false) @Builder.Default
    private int reviewCycle = 0;

    public Boolean getActive() { return status == FunctionStatus.PUBLISHED; }
    public boolean isRevision() { return revisionOfId != null; }
    /** Content may be changed in place only while the card is not public and not under review (incl. after a request for changes). */
    public boolean isEditable() {
        return status == FunctionStatus.DRAFT || status == FunctionStatus.DEACTIVATED || status == FunctionStatus.CHANGES_REQUESTED;
    }

    /** Copies every editorial field (content, translations, organization, category, verification) but not identity or workflow state. */
    public void copyContentFrom(OrgFunction source) {
        name = source.name;
        description = source.description;
        nameTranslations = copyTranslations(source.nameTranslations);
        descriptionTranslations = copyTranslations(source.descriptionTranslations);
        organizationId = source.organizationId;
        requirements = source.requirements;
        functionCategory = source.functionCategory;
        sourceLanguage = source.sourceLanguage;
        for (InstructionField field : InstructionField.values()) {
            field.set(this, field.get(source));
        }
        officialSourceUrl = source.officialSourceUrl;
        deliveryChannel = source.deliveryChannel;
        onlineServiceUrl = source.onlineServiceUrl;
        provisionLevel = source.provisionLevel;
        riskLevel = source.riskLevel;
        riskNotes = source.riskNotes;
        documentDetails = source.documentDetails == null || source.documentDetails.isEmpty() ? null : new ArrayList<>(source.documentDetails);
        Map<String, Map<String, TranslatedText>> instructions = new LinkedHashMap<>();
        if (source.instructionTranslations != null) {
            source.instructionTranslations.forEach((key, values) -> instructions.put(key, copyTranslations(values)));
        }
        instructionTranslations = instructions;
        lastVerifiedAt = source.lastVerifiedAt;
        verifiedByUserId = source.verifiedByUserId;
        verificationOutdated = source.verificationOutdated;
    }

    private static Map<String, TranslatedText> copyTranslations(Map<String, TranslatedText> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }
    /** Never null: cards without structured details return an empty list. */
    public List<DocumentDetail> documentDetailsOrEmpty() {
        return documentDetails == null ? List.of() : documentDetails;
    }

    public String getCategory() { return functionCategory == null ? null : functionCategory.getName(); }

    public Map<String, TranslatedText> instructionTranslationsOf(InstructionField field) {
        Map<String, TranslatedText> values = instructionTranslations == null ? null : instructionTranslations.get(field.key());
        return values == null ? Map.of() : values;
    }

    public boolean markVerificationOutdated() {
        if (lastVerifiedAt == null || verificationOutdated) {
            return false;
        }
        verificationOutdated = true;
        return true;
    }

    public VerificationStatus verificationStatus(Instant now) {
        return VerificationStatus.of(lastVerifiedAt, verificationOutdated, now);
    }
}
