package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

import java.util.List;
import java.util.Map;

/**
 * Aggregate usefulness votes for catalog information. These measure whether visitors found the published text
 * helpful; they are not a rating of the organization, the government service or its staff.
 */
public record FeedbackReport(EngagementReport.PeriodInfo period, Totals totals, Totals previousTotals, List<ServiceFeedback> services) {

    public record Totals(long helpful, long notHelpful, long total) {
        public static Totals of(long helpful, long notHelpful) {
            return new Totals(helpful, notHelpful, helpful + notHelpful);
        }
    }

    public record ServiceFeedback(Long functionId, String name, Map<String, TranslatedText> nameTranslations, FunctionStatus status,
                                  Long organizationId, Long categoryId, String category, long helpful, long notHelpful, long total) {}
}
