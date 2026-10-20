package adliya.uz.functioncatalogservice.repository;


import java.time.LocalDate;
import java.util.List;

public interface FeedbackStatistics {

    Totals totals(StatisticsFilter filter, LocalDate from, LocalDate to);

    List<ServiceTotals> byService(StatisticsFilter filter, LocalDate from, LocalDate to);

    record Totals(long helpful, long notHelpful) {
        public static Totals none() { return new Totals(0, 0); }
    }

    record ServiceTotals(Long functionId, Long organizationId, long helpful, long notHelpful) {}
}
