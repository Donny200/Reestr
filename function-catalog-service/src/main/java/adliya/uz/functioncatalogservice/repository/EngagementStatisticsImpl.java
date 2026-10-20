package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.EngagementEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class EngagementStatisticsImpl implements EngagementStatistics {

    private static final String FROM = " FROM engagement_daily_counts e LEFT JOIN org_functions f ON f.id = e.function_id";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<EngagementEventType, Long> totals(StatisticsFilter filter, LocalDate from, LocalDate to) {
        MapSqlParameterSource parameters = StatisticsFilter.parameters(from, to);
        Map<EngagementEventType, Long> totals = new EnumMap<>(EngagementEventType.class);
        jdbc.query("SELECT e.event_type, SUM(e.event_count) AS total" + FROM + filter.where("e", parameters) + " GROUP BY e.event_type",
                parameters, row -> {
                    totals.put(EngagementEventType.valueOf(row.getString("event_type")), row.getLong("total"));
                });
        return totals;
    }

    @Override
    public List<DailyCount> daily(StatisticsFilter filter, LocalDate from, LocalDate to) {
        MapSqlParameterSource parameters = StatisticsFilter.parameters(from, to);
        return jdbc.query("SELECT e.activity_date, e.event_type, SUM(e.event_count) AS total" + FROM + filter.where("e", parameters)
                        + " GROUP BY e.activity_date, e.event_type ORDER BY e.activity_date",
                parameters, (row, index) -> new DailyCount(row.getObject("activity_date", LocalDate.class),
                        EngagementEventType.valueOf(row.getString("event_type")), row.getLong("total")));
    }

    @Override
    public List<SubjectCount> bySubject(StatisticsFilter filter, LocalDate from, LocalDate to) {
        MapSqlParameterSource parameters = StatisticsFilter.parameters(from, to);
        return jdbc.query("SELECT e.function_id, e.organization_id, e.event_type, SUM(e.event_count) AS total" + FROM
                        + filter.where("e", parameters) + " GROUP BY e.function_id, e.organization_id, e.event_type",
                parameters, (row, index) -> new SubjectCount(row.getObject("function_id", Long.class),
                        row.getObject("organization_id", Long.class), EngagementEventType.valueOf(row.getString("event_type")),
                        row.getLong("total")));
    }
}
