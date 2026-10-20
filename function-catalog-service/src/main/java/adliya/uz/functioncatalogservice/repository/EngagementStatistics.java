package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.EngagementEventType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface EngagementStatistics {

    Map<EngagementEventType, Long> totals(StatisticsFilter filter, LocalDate from, LocalDate to);

    List<DailyCount> daily(StatisticsFilter filter, LocalDate from, LocalDate to);

    List<SubjectCount> bySubject(StatisticsFilter filter, LocalDate from, LocalDate to);

    record DailyCount(LocalDate day, EngagementEventType type, long count) {}

    record SubjectCount(Long functionId, Long organizationId, EngagementEventType type, long count) {}
}
