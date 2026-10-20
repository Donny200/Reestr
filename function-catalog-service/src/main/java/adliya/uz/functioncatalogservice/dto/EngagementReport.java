package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.TranslatedText;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EngagementReport(
        PeriodInfo period, Granularity granularity,
        Map<EngagementEventType, Long> totals, Map<EngagementEventType, Long> previousTotals,
        List<SeriesPoint> series, List<SubjectEngagement> topServices
) {
    public enum Granularity { AUTO, DAY, WEEK }

    public enum SubjectType { SERVICE, ORGANIZATION, CATALOG }

    public record PeriodInfo(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, String timeZone) {
        public static PeriodInfo of(ReportingPeriod period, String timeZone) {
            ReportingPeriod previous = period.previous();
            return new PeriodInfo(period.from(), period.to(), previous.from(), previous.to(), timeZone);
        }
    }

    public record SeriesPoint(LocalDate start, LocalDate end, Map<EngagementEventType, Long> counts) {}

    public record SubjectEngagement(SubjectType subject, Long functionId, String name, Map<String, TranslatedText> nameTranslations,
                                    FunctionStatus status, Long organizationId, Long categoryId, String category,
                                    long views, long actions, Map<EngagementEventType, Long> counts) {}
}
