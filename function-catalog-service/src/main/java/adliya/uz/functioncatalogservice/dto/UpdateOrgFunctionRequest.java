package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateOrgFunctionRequest(
        @Size(min = 1, max = 150) @Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL) String name,
        @Size(max = 500) String description,
        @Positive Long organizationId,
        @Size(max = 500) String requirements,
        @Size(max = 100) String category,
        @Positive Long categoryId,
        @Pattern(regexp = "[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*") @Size(max = 35) String sourceLanguage,
        @Valid ServiceInstructions instructions,
        @Size(max = 500) @Pattern(regexp = OfficialSourceUrl.PATTERN, message = OfficialSourceUrl.MESSAGE) String officialSourceUrl,
        /** Null keeps the current details (details of removed document lines are dropped); a list replaces them. */
        @Size(max = DocumentDetail.MAX_ITEMS) List<@Valid DocumentDetail> documentDetails,
        /** Staff-only digitalization/decentralization/risk profile; null keeps the current one. */
        @Valid ServiceProfile profile,
        /** Editor step the author saved from (general, mechanism, digitalization, decentralization, other). */
        @Pattern(regexp = CreateOrgFunctionRequest.EDITOR_STEP_PATTERN) String editorStep
) {
    public UpdateOrgFunctionRequest(String name, String description, Long organizationId, String requirements, String category, Long categoryId,
                                    String sourceLanguage, ServiceInstructions instructions, String officialSourceUrl, List<DocumentDetail> documentDetails) {
        this(name, description, organizationId, requirements, category, categoryId, sourceLanguage, instructions, officialSourceUrl,
                documentDetails, null, null);
    }
    public UpdateOrgFunctionRequest(String name, String description, Long organizationId, String requirements, String category,
                                    Long categoryId, String sourceLanguage, ServiceInstructions instructions, String officialSourceUrl) {
        this(name, description, organizationId, requirements, category, categoryId, sourceLanguage, instructions, officialSourceUrl, null, null, null);
    }
    public UpdateOrgFunctionRequest(String name, String description, Long organizationId, String requirements,
                                    String category, Long categoryId, String sourceLanguage) {
        this(name, description, organizationId, requirements, category, categoryId, sourceLanguage, null, null, null, null, null);
    }
}
