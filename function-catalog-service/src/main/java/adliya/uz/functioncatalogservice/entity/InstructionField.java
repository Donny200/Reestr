package adliya.uz.functioncatalogservice.entity;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public enum InstructionField {
    WHO_CAN_USE("whoCanUse", 2000, false, OrgFunction::getWhoCanUse, OrgFunction::setWhoCanUse),
    STEPS("steps", 4000, true, OrgFunction::getSteps, OrgFunction::setSteps),
    REQUIRED_DOCUMENTS("requiredDocuments", 4000, true, OrgFunction::getRequiredDocuments, OrgFunction::setRequiredDocuments),
    WHERE_HOW_TO_APPLY("whereHowToApply", 2000, false, OrgFunction::getWhereHowToApply, OrgFunction::setWhereHowToApply),
    PROCESSING_TIME("processingTime", 500, false, OrgFunction::getProcessingTime, OrgFunction::setProcessingTime),
    FEE("fee", 500, false, OrgFunction::getFee, OrgFunction::setFee);

    public static final int MAX_LIST_ITEMS = 50;

    private final String key;
    private final int maxLength;
    private final boolean list;
    private final Function<OrgFunction, String> getter;
    private final BiConsumer<OrgFunction, String> setter;

    InstructionField(String key, int maxLength, boolean list,
                     Function<OrgFunction, String> getter, BiConsumer<OrgFunction, String> setter) {
        this.key = key;
        this.maxLength = maxLength;
        this.list = list;
        this.getter = getter;
        this.setter = setter;
    }

    public String key() { return key; }
    public int maxLength() { return maxLength; }
    public boolean list() { return list; }
    public String get(OrgFunction function) { return getter.apply(function); }
    public void set(OrgFunction function, String value) { setter.accept(function, value); }

    public static Optional<InstructionField> byKey(String key) {
        return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
    }
}
