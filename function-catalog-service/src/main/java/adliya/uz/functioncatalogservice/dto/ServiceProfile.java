package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.DeliveryChannel;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ProvisionLevel;
import adliya.uz.functioncatalogservice.entity.RiskLevel;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Staff-only part of a card filled in the "Digitalization" and "Decentralization and risk assessment" editor steps.
 * Never included in public responses. Every value is optional while drafting; {@link #submissionErrors} lists what a
 * submission for review additionally requires.
 */
public record ServiceProfile(
        DeliveryChannel deliveryChannel,
        @Size(max = 500) @Pattern(regexp = OfficialSourceUrl.PATTERN, message = OfficialSourceUrl.MESSAGE) String onlineServiceUrl,
        ProvisionLevel provisionLevel,
        RiskLevel riskLevel,
        @Size(max = 2000) String riskNotes
) {
    public static final String PREFIX = "profile.";

    public static ServiceProfile of(OrgFunction function) {
        return new ServiceProfile(function.getDeliveryChannel(), function.getOnlineServiceUrl(), function.getProvisionLevel(),
                function.getRiskLevel(), function.getRiskNotes());
    }

    /** Field errors (keyed by request path) that block a submission for review. */
    public static java.util.Map<String, String> submissionErrors(OrgFunction function) {
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (function.getDeliveryChannel() != null && function.getDeliveryChannel() != DeliveryChannel.IN_PERSON
                && function.getOnlineServiceUrl() == null) {
            errors.put(PREFIX + "onlineServiceUrl", "A link to the online service is required when it can be received online");
        }
        if (function.getRiskLevel() == RiskLevel.HIGH && function.getRiskNotes() == null) {
            errors.put(PREFIX + "riskNotes", "Describe the risk and how it is mitigated when the risk level is high");
        }
        return errors;
    }
}
