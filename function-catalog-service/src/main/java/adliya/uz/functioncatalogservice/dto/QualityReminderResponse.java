package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.entity.QualityReminder;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QualityReminderResponse(Long id, Long functionId, String functionName, Map<String, TranslatedText> functionNameTranslations,
                                      FunctionStatus functionStatus, Long organizationId, QualityIssueType issue,
                                      Instant detectedAt, Instant notifiedAt, Long assignedUserId, String assignedUserName) {

    public static QualityReminderResponse of(QualityReminder reminder, OrgFunction function) {
        return new QualityReminderResponse(reminder.getId(), reminder.getFunctionId(),
                function == null ? null : function.getName(), function == null ? null : function.getNameTranslations(),
                function == null ? null : function.getStatus(), reminder.getOrganizationId(), reminder.getIssue(),
                reminder.getDetectedAt(), reminder.getNotifiedAt(), reminder.getAssignedUserId(),
                function == null || !java.util.Objects.equals(function.getAssignedUserId(), reminder.getAssignedUserId()) ? null : function.getAssignedUserName());
    }

    public record Page(long total, List<QualityReminderResponse> items) {}
}
