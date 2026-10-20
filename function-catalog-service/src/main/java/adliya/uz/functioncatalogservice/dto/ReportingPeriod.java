package adliya.uz.functioncatalogservice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record ReportingPeriod(LocalDate from, LocalDate to) {

    public static ReportingPeriod resolve(LocalDate from, LocalDate to, LocalDate today, int defaultDays, int maxDays) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(defaultDays - 1L) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        ReportingPeriod period = new ReportingPeriod(start, end);
        if (period.days() > maxDays) {
            throw new IllegalArgumentException("The period may span at most " + maxDays + " days");
        }
        return period;
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public ReportingPeriod previous() {
        return new ReportingPeriod(from.minusDays(days()), from.minusDays(1));
    }

    public Instant start(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    public Instant endExclusive(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
