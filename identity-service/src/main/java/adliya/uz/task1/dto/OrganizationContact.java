package adliya.uz.task1.dto;

import adliya.uz.task1.entity.Organization;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationContact(
        @Size(max = 500, message = "Address must be at most 500 characters") String address,
        @Size(max = 40, message = "Phone must be at most 40 characters")
        @Pattern(regexp = PHONE_PATTERN, message = "Phone may contain digits, spaces, brackets, hyphens and a leading +") String phone,
        @Size(max = 500, message = "Working hours must be at most 500 characters") String workingHours,
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90") Double latitude,
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180") Double longitude,
        @Size(max = 500, message = "Map link must be at most 500 characters")
        @Pattern(regexp = OfficialLink.PATTERN, message = OfficialLink.MESSAGE) String mapUrl,
        @Size(max = 20, message = "Region code must be at most 20 characters")
        @Pattern(regexp = "^\\s*$|^\\s*[0-9A-Za-z-]{1,20}\\s*$", message = "Region code may contain letters, digits and hyphens") String regionCode
) {
    public static final String PHONE_PATTERN = "^\\s*$|^\\s*\\+?[0-9][0-9 ()\\-]{4,38}\\s*$";

    public static OrganizationContact of(Organization organization) {
        return new OrganizationContact(organization.getAddress(), organization.getPhone(), organization.getWorkingHours(),
                organization.getLatitude(), organization.getLongitude(), organization.getMapUrl(), organization.getRegionCode());
    }
}
