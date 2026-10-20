package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.ReportCategory;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import jakarta.validation.constraints.*;

public record SubmitReportRequest(
        @NotNull ReportEntityType entityType,
        @NotNull @Positive Long entityId,
        @NotNull ReportCategory category,
        @NotBlank @Size(min = 10, max = 2000) String description,
        @Size(max = 254) @Pattern(regexp = CONTACT_PATTERN, message = "must be an email address or a phone number") String contact,
        @Size(max = 35) @Pattern(regexp = "[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*") String language,
        @Size(max = 200) String website
) {
    public static final String CONTACT_PATTERN =
            "^\\s*$|^\\s*[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}\\s*$|^\\s*\\+?[0-9][0-9 ()\\-]{5,24}\\s*$";
}
