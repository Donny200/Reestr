package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.DocumentDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateOrgFunctionRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Positive Long organizationId,
        @Size(max = 500) String requirements,
        @Size(max = 100) String category,
        @Positive Long categoryId,
        @Pattern(regexp = "[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*") @Size(max = 35) String sourceLanguage,
        @Valid ServiceInstructions instructions,
        @Size(max = 500) @Pattern(regexp = OfficialSourceUrl.PATTERN, message = OfficialSourceUrl.MESSAGE) String officialSourceUrl,
        /** Optional structured details of required documents; each entry names a line of instructions.requiredDocuments. */
        @Size(max = DocumentDetail.MAX_ITEMS) List<@Valid DocumentDetail> documentDetails,
        /** Staff-only digitalization/decentralization/risk profile; null keeps the current one. */
        @Valid ServiceProfile profile,
        /** Editor step the author saved from (general, mechanism, digitalization, decentralization, other). */
        @Pattern(regexp = EDITOR_STEP_PATTERN) String editorStep
) {
    public static final String EDITOR_STEP_PATTERN = "general|mechanism|digitalization|decentralization|other";

    public CreateOrgFunctionRequest(String name, String description, Long organizationId, String requirements, String category, Long categoryId,
                                    String sourceLanguage, ServiceInstructions instructions, String officialSourceUrl, List<DocumentDetail> documentDetails) {
        this(name, description, organizationId, requirements, category, categoryId, sourceLanguage, instructions, officialSourceUrl,
                documentDetails, null, null);
    }
    public CreateOrgFunctionRequest(String name, String description, Long organizationId, String requirements, String category,
                                    Long categoryId, String sourceLanguage, ServiceInstructions instructions, String officialSourceUrl) {
        this(name, description, organizationId, requirements, category, categoryId, sourceLanguage, instructions, officialSourceUrl, null, null, null);
    }
    public CreateOrgFunctionRequest(String name, String description, Long organizationId, String requirements,
                                    String category, Long categoryId, String sourceLanguage) {
        this(name, description, organizationId, requirements, category, categoryId, sourceLanguage, null, null, null, null, null);
    }
    public CreateOrgFunctionRequest(String name, String description, Long organizationId, String requirements, String category, Long categoryId) {
        this(name, description, organizationId, requirements, category, categoryId, null);
    }
    public CreateOrgFunctionRequest(String name, String description, Long organizationId, String requirements, String category) {
        this(name, description, organizationId, requirements, category, null);
    }
}
