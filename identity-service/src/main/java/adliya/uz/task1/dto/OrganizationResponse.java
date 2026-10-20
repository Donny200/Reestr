package adliya.uz.task1.dto;

import adliya.uz.task1.entity.Organization;
import adliya.uz.task1.entity.TranslatedText;
import adliya.uz.task1.entity.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {
    private Long id;
    private String name;
    private String description;
    private Map<String, TranslatedText> nameTranslations;
    private Map<String, TranslatedText> descriptionTranslations;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private OrganizationContact contact;
    private String officialSourceUrl;
    private Instant lastVerifiedAt;
    private Long verifiedByUserId;
    private VerificationStatus verificationStatus;

    public static OrganizationResponse from(Organization org) {
        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .description(org.getDescription())
                .nameTranslations(org.getNameTranslations())
                .descriptionTranslations(org.getDescriptionTranslations())
                .enabled(org.getEnabled())
                .createdAt(org.getCreatedAt())
                .contact(OrganizationContact.of(org))
                .officialSourceUrl(org.getOfficialSourceUrl())
                .lastVerifiedAt(org.getLastVerifiedAt())
                .verifiedByUserId(org.getVerifiedByUserId())
                .verificationStatus(org.verificationStatus(Instant.now()))
                .build();
    }
}
