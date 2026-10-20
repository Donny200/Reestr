package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import adliya.uz.functioncatalogservice.dto.EngagementReport;
import adliya.uz.functioncatalogservice.dto.EngagementReport.Granularity;
import adliya.uz.functioncatalogservice.dto.EngagementReport.PeriodInfo;
import adliya.uz.functioncatalogservice.dto.EngagementReport.SeriesPoint;
import adliya.uz.functioncatalogservice.dto.EngagementReport.SubjectEngagement;
import adliya.uz.functioncatalogservice.dto.EngagementReport.SubjectType;
import adliya.uz.functioncatalogservice.dto.ReportingPeriod;
import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.repository.EngagementDailyCountRepository;
import adliya.uz.functioncatalogservice.repository.EngagementStatistics.DailyCount;
import adliya.uz.functioncatalogservice.repository.EngagementStatistics.SubjectCount;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.StatisticsFilter;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EngagementInsightsService {

    public static final int TOP_SERVICES = 10;
    private static final int DAILY_SERIES_MAX_DAYS = 62;
    private static final Comparator<SubjectEngagement> MOST_VIEWED = Comparator
            .comparingLong(SubjectEngagement::views).reversed()
            .thenComparing(Comparator.comparingLong(SubjectEngagement::actions).reversed())
            .thenComparing(item -> item.functionId() == null ? Long.MAX_VALUE : item.functionId());

    private final EngagementDailyCountRepository counts;
    private final OrgFunctionRepository functions;
    private final CatalogAccess access;
    private final AnalyticsProperties properties;

    public ReportingPeriod period(LocalDate from, LocalDate to) {
        return ReportingPeriod.resolve(from, to, LocalDate.now(properties.getZone()),
                properties.getDefaultPeriodDays(), properties.getMaxPeriodDays());
    }

    public PeriodInfo periodInfo(ReportingPeriod period) {
        return PeriodInfo.of(period, properties.getZone().getId());
    }

    public EngagementReport report(Long organizationId, Long categoryId, LocalDate from, LocalDate to, Granularity granularity) {
        return activity(organizationId, categoryId, from, to, granularity).report();
    }

    /** The report together with every subject of the period, most viewed first, so the subjects are queried once. */
    public Activity activity(Long organizationId, Long categoryId, LocalDate from, LocalDate to, Granularity granularity) {
        OrganizationScope scope = access.scope(organizationId);
        ReportingPeriod period = period(from, to);
        ReportingPeriod previous = period.previous();
        Granularity effective = effective(granularity, period);
        StatisticsFilter filter = new StatisticsFilter(scope, categoryId);
        if (scope.empty()) {
            return new Activity(new EngagementReport(periodInfo(period), effective, complete(Map.of()), complete(Map.of()),
                    series(List.of(), period, effective), List.of()), List.of());
        }
        List<SubjectEngagement> subjects = subjects(filter, period).stream().sorted(MOST_VIEWED).toList();
        List<SubjectEngagement> top = subjects.stream()
                .filter(item -> item.subject() == SubjectType.SERVICE)
                .limit(TOP_SERVICES)
                .toList();
        return new Activity(new EngagementReport(periodInfo(period), effective,
                complete(counts.totals(filter, period.from(), period.to())),
                complete(counts.totals(filter, previous.from(), previous.to())),
                series(counts.daily(filter, period.from(), period.to()), period, effective), top), subjects);
    }

    public List<SubjectEngagement> subjects(Long organizationId, Long categoryId, ReportingPeriod period) {
        OrganizationScope scope = access.scope(organizationId);
        if (scope.empty()) {
            return List.of();
        }
        return subjects(new StatisticsFilter(scope, categoryId), period).stream().sorted(MOST_VIEWED).toList();
    }

    public List<SeriesPoint> daily(Long organizationId, Long categoryId, ReportingPeriod period) {
        OrganizationScope scope = access.scope(organizationId);
        List<DailyCount> rows = scope.empty() ? List.of() : counts.daily(new StatisticsFilter(scope, categoryId), period.from(), period.to());
        return series(rows, period, Granularity.DAY);
    }

    @Transactional
    public int purgeExpired() {
        LocalDate cutoff = LocalDate.now(properties.getZone()).minusDays(properties.getRetention().toDays());
        return counts.deleteOlderThan(cutoff);
    }

    private List<SubjectEngagement> subjects(StatisticsFilter filter, ReportingPeriod period) {
        Map<SubjectKey, Map<EngagementEventType, Long>> grouped = new LinkedHashMap<>();
        for (SubjectCount row : counts.bySubject(filter, period.from(), period.to())) {
            grouped.computeIfAbsent(new SubjectKey(row.functionId(), row.organizationId()), key -> new EnumMap<>(EngagementEventType.class))
                    .merge(row.type(), row.count(), Long::sum);
        }
        Set<Long> functionIds = grouped.keySet().stream().map(SubjectKey::functionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, OrgFunction> cards = functions.mapById(functionIds);
        return grouped.entrySet().stream().map(entry -> subject(entry.getKey(), cards.get(entry.getKey().functionId()),
                complete(entry.getValue()))).toList();
    }

    private static SubjectEngagement subject(SubjectKey key, OrgFunction card, Map<EngagementEventType, Long> values) {
        long views = values.get(EngagementEventType.SERVICE_VIEW);
        long actions = EngagementEventType.ACTIONS.stream().mapToLong(values::get).sum();
        if (key.functionId() == null) {
            SubjectType type = key.organizationId() == null ? SubjectType.CATALOG : SubjectType.ORGANIZATION;
            return new SubjectEngagement(type, null, null, null, null, key.organizationId(), null, null, views, actions, values);
        }
        return new SubjectEngagement(SubjectType.SERVICE, key.functionId(), card == null ? null : card.getName(),
                card == null ? null : card.getNameTranslations(), card == null ? null : card.getStatus(), key.organizationId(),
                card == null || card.getFunctionCategory() == null ? null : card.getFunctionCategory().getId(),
                card == null ? null : card.getCategory(), views, actions, values);
    }

    private static Granularity effective(Granularity requested, ReportingPeriod period) {
        if (requested == null || requested == Granularity.AUTO) {
            return period.days() <= DAILY_SERIES_MAX_DAYS ? Granularity.DAY : Granularity.WEEK;
        }
        return requested;
    }

    private static List<SeriesPoint> series(List<DailyCount> rows, ReportingPeriod period, Granularity granularity) {
        Map<LocalDate, Map<EngagementEventType, Long>> buckets = new TreeMap<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            buckets.computeIfAbsent(bucketStart(day, period, granularity), key -> new EnumMap<>(EngagementEventType.class));
        }
        for (DailyCount row : rows) {
            buckets.get(bucketStart(row.day(), period, granularity)).merge(row.type(), row.count(), Long::sum);
        }
        return buckets.entrySet().stream()
                .map(entry -> new SeriesPoint(entry.getKey(), bucketEnd(entry.getKey(), period, granularity), complete(entry.getValue())))
                .toList();
    }

    private static LocalDate bucketStart(LocalDate day, ReportingPeriod period, Granularity granularity) {
        if (granularity == Granularity.DAY) {
            return day;
        }
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.isBefore(period.from()) ? period.from() : monday;
    }

    private static LocalDate bucketEnd(LocalDate start, ReportingPeriod period, Granularity granularity) {
        if (granularity == Granularity.DAY) {
            return start;
        }
        LocalDate sunday = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return sunday.isAfter(period.to()) ? period.to() : sunday;
    }

    private static Map<EngagementEventType, Long> complete(Map<EngagementEventType, Long> values) {
        Map<EngagementEventType, Long> result = new EnumMap<>(EngagementEventType.class);
        for (EngagementEventType type : EngagementEventType.values()) {
            result.put(type, values.getOrDefault(type, 0L));
        }
        return result;
    }

    public record Activity(EngagementReport report, List<SubjectEngagement> subjects) {}

    private record SubjectKey(Long functionId, Long organizationId) {}
}
