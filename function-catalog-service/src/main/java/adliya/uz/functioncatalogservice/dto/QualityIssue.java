package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.QualityIssueType;

import java.util.List;

public record QualityIssue(QualityIssueType type, String reason, List<String> details) {

    public static final String NEVER_VERIFIED = "NEVER_VERIFIED";
    public static final String RECHECK_DUE = "RECHECK_DUE";
    public static final String CHANGED_SINCE_VERIFICATION = "CHANGED_SINCE_VERIFICATION";
    public static final String UNUSABLE = "UNUSABLE";
    public static final String REQUIRED_FIELDS = "REQUIRED_FIELDS";
    public static final String LANGUAGES = "LANGUAGES";

    public QualityIssue {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
