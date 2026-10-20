package adliya.uz.functioncatalogservice.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportingPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Test void defaultsToTheLastThirtyDaysIncludingToday() {
        ReportingPeriod period = ReportingPeriod.resolve(null, null, TODAY, 30, 366);
        assertThat(period).isEqualTo(new ReportingPeriod(LocalDate.of(2026, 9, 10), TODAY));
        assertThat(period.days()).isEqualTo(30);
    }

    @Test void previousPeriodIsImmediatelyBeforeAndEquallyLong() {
        ReportingPeriod march = ReportingPeriod.resolve(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), TODAY, 30, 366);
        assertThat(march.previous()).isEqualTo(new ReportingPeriod(LocalDate.of(2026, 1, 29), LocalDate.of(2026, 2, 28)));
        assertThat(march.previous().days()).isEqualTo(march.days());
        ReportingPeriod single = ReportingPeriod.resolve(TODAY, TODAY, TODAY, 30, 366);
        assertThat(single.previous()).isEqualTo(new ReportingPeriod(TODAY.minusDays(1), TODAY.minusDays(1)));
    }

    @Test void rejectsReversedAndOverlongPeriods() {
        assertThatThrownBy(() -> ReportingPeriod.resolve(TODAY, TODAY.minusDays(1), TODAY, 30, 366))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportingPeriod.resolve(TODAY.minusDays(366), TODAY, TODAY, 30, 366))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("366");
    }

    @Test void daysAreCalendarDaysInTheConfiguredZone() {
        ReportingPeriod period = new ReportingPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        ZoneId tashkent = ZoneId.of("Asia/Tashkent");
        assertThat(period.start(tashkent)).isEqualTo(Instant.parse("2026-09-30T19:00:00Z"));
        assertThat(period.endExclusive(tashkent)).isEqualTo(Instant.parse("2026-10-01T19:00:00Z"));
    }
}
