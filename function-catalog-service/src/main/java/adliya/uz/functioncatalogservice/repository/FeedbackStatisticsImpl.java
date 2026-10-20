package adliya.uz.functioncatalogservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
public class FeedbackStatisticsImpl implements FeedbackStatistics {

    private static final String FROM = " FROM feedback_daily_counts d LEFT JOIN org_functions f ON f.id = d.function_id";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Totals totals(StatisticsFilter filter, LocalDate from, LocalDate to) {
        MapSqlParameterSource parameters = StatisticsFilter.parameters(from, to);
        return jdbc.query("SELECT COALESCE(SUM(d.helpful_count), 0) AS helpful, COALESCE(SUM(d.not_helpful_count), 0) AS not_helpful"
                        + FROM + filter.where("d", parameters), parameters,
                (row, index) -> new Totals(row.getLong("helpful"), row.getLong("not_helpful")))
                .stream().findFirst().orElse(Totals.none());
    }

    @Override
    public List<ServiceTotals> byService(StatisticsFilter filter, LocalDate from, LocalDate to) {
        MapSqlParameterSource parameters = StatisticsFilter.parameters(from, to);
        return jdbc.query("SELECT d.function_id, d.organization_id, SUM(d.helpful_count) AS helpful, SUM(d.not_helpful_count) AS not_helpful"
                        + FROM + filter.where("d", parameters) + " GROUP BY d.function_id, d.organization_id", parameters,
                (row, index) -> new ServiceTotals(row.getObject("function_id", Long.class), row.getObject("organization_id", Long.class),
                        row.getLong("helpful"), row.getLong("not_helpful")));
    }
}
