package adliya.uz.functioncatalogservice.entity;

import java.util.EnumSet;
import java.util.Set;

import static adliya.uz.functioncatalogservice.entity.ReportEntityType.FUNCTION;
import static adliya.uz.functioncatalogservice.entity.ReportEntityType.ORGANIZATION;

public enum ReportCategory {
    INCORRECT_INFORMATION(FUNCTION, ORGANIZATION),
    OUTDATED_INFORMATION(FUNCTION, ORGANIZATION),
    DOCUMENTS_OR_STEPS(FUNCTION),
    FEES_OR_TIMING(FUNCTION),
    CONTACT_DETAILS(ORGANIZATION),
    BROKEN_LINK(FUNCTION, ORGANIZATION),
    TRANSLATION(FUNCTION, ORGANIZATION),
    OTHER(FUNCTION, ORGANIZATION);

    private final Set<ReportEntityType> applicable;

    ReportCategory(ReportEntityType first, ReportEntityType... rest) {
        this.applicable = EnumSet.of(first, rest);
    }

    public boolean appliesTo(ReportEntityType type) {
        return applicable.contains(type);
    }
}
