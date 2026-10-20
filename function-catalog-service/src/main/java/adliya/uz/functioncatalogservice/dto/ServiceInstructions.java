package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.InstructionField;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import jakarta.validation.constraints.Size;

public record ServiceInstructions(
        @Size(max = 2000) String whoCanUse,
        @Size(max = 4000) String steps,
        @Size(max = 4000) String requiredDocuments,
        @Size(max = 2000) String whereHowToApply,
        @Size(max = 500) String processingTime,
        @Size(max = 500) String fee
) {
    public static ServiceInstructions of(OrgFunction function) {
        return new ServiceInstructions(function.getWhoCanUse(), function.getSteps(), function.getRequiredDocuments(),
                function.getWhereHowToApply(), function.getProcessingTime(), function.getFee());
    }

    public String value(InstructionField field) {
        return switch (field) {
            case WHO_CAN_USE -> whoCanUse;
            case STEPS -> steps;
            case REQUIRED_DOCUMENTS -> requiredDocuments;
            case WHERE_HOW_TO_APPLY -> whereHowToApply;
            case PROCESSING_TIME -> processingTime;
            case FEE -> fee;
        };
    }
}
