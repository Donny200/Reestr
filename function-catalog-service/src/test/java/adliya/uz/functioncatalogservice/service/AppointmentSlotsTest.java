package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.AppointmentClosure;
import adliya.uz.functioncatalogservice.entity.AppointmentSchedule;
import adliya.uz.functioncatalogservice.entity.WorkingDay;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentSlotsTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    /** Monday 2026-10-19 07:00 in Tashkent. */
    private static final Instant MONDAY_MORNING = ZonedDateTime.of(2026, 10, 19, 7, 0, 0, 0, TASHKENT).toInstant();
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private static AppointmentSchedule schedule(int slot, int capacity, int horizon, int notice, WorkingDay... days) {
        return AppointmentSchedule.builder().organizationId(10L).enabled(true).timeZone("Asia/Tashkent").slotMinutes(slot)
                .capacity(capacity).bookingHorizonDays(horizon).minNoticeMinutes(notice).weeklyHours(List.of(days))
                .updatedAt(Instant.EPOCH).build();
    }

    private static Instant at(LocalDate date, String time) {
        return ZonedDateTime.of(date, LocalTime.parse(time), TASHKENT).toInstant();
    }

    @Test void slotsFollowOpeningHoursAndSkipTheBreak() {
        var schedule = schedule(60, 1, 7, 0, new WorkingDay(DayOfWeek.MONDAY, "09:00", "14:00", "12:00", "13:00"));
        var days = AppointmentSlots.days(schedule, List.of(), Map.of(), MONDAY_MORNING, MONDAY, 1);
        assertThat(days).hasSize(1);
        assertThat(days.get(0).open()).isTrue();
        assertThat(days.get(0).slots()).extracting(slot -> slot.start().toString()).containsExactly("09:00", "10:00", "11:00", "13:00");
        // Instants are computed in the organization's time zone (UTC+5).
        assertThat(days.get(0).slots().get(0).startsAt()).isEqualTo(Instant.parse("2026-10-19T04:00:00Z"));
    }

    @Test void aSlotThatDoesNotFitBeforeClosingIsNotOffered() {
        var schedule = schedule(45, 1, 7, 0, new WorkingDay(DayOfWeek.MONDAY, "09:00", "10:20", null, null));
        assertThat(AppointmentSlots.days(schedule, List.of(), Map.of(), MONDAY_MORNING, MONDAY, 1).get(0).slots())
                .extracting(slot -> slot.start().toString()).containsExactly("09:00");
    }

    @Test void daysWithoutHoursHolidaysAndUnavailablePeriodsHaveNoSlots() {
        var schedule = schedule(60, 1, 7, 0,
                new WorkingDay(DayOfWeek.MONDAY, "09:00", "12:00", null, null),
                new WorkingDay(DayOfWeek.TUESDAY, "09:00", "12:00", null, null),
                new WorkingDay(DayOfWeek.WEDNESDAY, "09:00", "12:00", null, null));
        var holiday = AppointmentClosure.builder().kind(AppointmentClosure.Kind.HOLIDAY).startDate(MONDAY.plusDays(1)).endDate(MONDAY.plusDays(1)).build();
        var meeting = AppointmentClosure.builder().kind(AppointmentClosure.Kind.UNAVAILABLE).startDate(MONDAY.plusDays(2)).endDate(MONDAY.plusDays(2))
                .startTime(LocalTime.of(10, 30)).endTime(LocalTime.of(11, 0)).build();
        var days = AppointmentSlots.days(schedule, List.of(holiday, meeting), Map.of(), MONDAY_MORNING, MONDAY, 4);
        assertThat(days).extracting(AppointmentSlots.Day::open).containsExactly(true, false, true, false);
        assertThat(days.get(1).slots()).isEmpty();
        assertThat(days.get(2).slots()).extracting(slot -> slot.start().toString()).containsExactly("09:00", "11:00");
        assertThat(days.get(3).slots()).isEmpty();
    }

    @Test void minimumNoticeHidesSlotsThatStartTooSoonAndPastDaysAreNeverListed() {
        var schedule = schedule(60, 1, 7, 150, new WorkingDay(DayOfWeek.MONDAY, "09:00", "12:00", null, null));
        var days = AppointmentSlots.days(schedule, List.of(), Map.of(), MONDAY_MORNING, MONDAY.minusDays(3), 1);
        assertThat(days.get(0).date()).isEqualTo(MONDAY);
        assertThat(days.get(0).slots()).extracting(slot -> slot.start().toString()).containsExactly("10:00", "11:00");
    }

    @Test void bookingWindowLimitsHowFarAheadVisitorsSee() {
        var schedule = schedule(60, 1, 3, 0, new WorkingDay(DayOfWeek.MONDAY, "09:00", "10:00", null, null),
                new WorkingDay(DayOfWeek.THURSDAY, "09:00", "10:00", null, null));
        var days = AppointmentSlots.days(schedule, List.of(), Map.of(), MONDAY_MORNING, MONDAY, 31);
        assertThat(days).hasSize(3);
        assertThat(AppointmentSlots.bookable(schedule, List.of(), Map.of(), MONDAY_MORNING, at(MONDAY.plusDays(3), "09:00"))).isEmpty();
        assertThat(AppointmentSlots.bookable(schedule, List.of(), Map.of(), MONDAY_MORNING, at(MONDAY, "09:00"))).isPresent();
    }

    @Test void fullyBookedSlotsDisappearAndCapacityAllowsParallelPlaces() {
        var schedule = schedule(60, 2, 7, 0, new WorkingDay(DayOfWeek.MONDAY, "09:00", "11:00", null, null));
        Instant nine = at(MONDAY, "09:00");
        Instant ten = at(MONDAY, "10:00");
        var slots = AppointmentSlots.days(schedule, List.of(), Map.of(nine, 2L, ten, 1L), MONDAY_MORNING, MONDAY, 1).get(0).slots();
        assertThat(slots).extracting(slot -> slot.start().toString()).containsExactly("10:00");
        assertThat(slots.get(0).remaining()).isEqualTo(1);
        assertThat(AppointmentSlots.bookable(schedule, List.of(), Map.of(nine, 2L), MONDAY_MORNING, nine)).isEmpty();
    }

    @Test void onlyExactSlotStartsAreBookable() {
        var schedule = schedule(30, 1, 7, 0, new WorkingDay(DayOfWeek.MONDAY, "09:00", "11:00", null, null));
        assertThat(AppointmentSlots.bookable(schedule, List.of(), Map.of(), MONDAY_MORNING, at(MONDAY, "09:30"))).isPresent();
        assertThat(AppointmentSlots.bookable(schedule, List.of(), Map.of(), MONDAY_MORNING, at(MONDAY, "09:10"))).isEmpty();
        assertThat(AppointmentSlots.bookable(schedule, List.of(), Map.of(), MONDAY_MORNING, at(MONDAY, "08:30"))).isEmpty();
    }

    @Test void weeklyHoursAreValidated() {
        assertThatThrownBy(() -> AppointmentService.weeklyHours(List.of(new WorkingDay(DayOfWeek.MONDAY, "10:00", "09:00", null, null)), 30))
                .isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> AppointmentService.weeklyHours(List.of(new WorkingDay(DayOfWeek.MONDAY, "09:00", "18:00", "13:00", null)), 30))
                .isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> AppointmentService.weeklyHours(List.of(new WorkingDay(DayOfWeek.MONDAY, "09:00", "18:00", "08:00", "09:30")), 30))
                .isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> AppointmentService.weeklyHours(List.of(new WorkingDay(DayOfWeek.MONDAY, "09:00", "18:00", null, null),
                new WorkingDay(DayOfWeek.MONDAY, "09:00", "12:00", null, null)), 30)).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> AppointmentService.weeklyHours(List.of(new WorkingDay(DayOfWeek.MONDAY, "09:00", "09:20", null, null)), 30))
                .isInstanceOf(InvalidFieldException.class);
        assertThat(AppointmentService.weeklyHours(List.of(new WorkingDay(DayOfWeek.FRIDAY, "09:00", "18:00", "", ""),
                new WorkingDay(DayOfWeek.MONDAY, "09:00", "18:00", "13:00", "14:00")), 30))
                .extracting(WorkingDay::day).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test void phoneNumbersAreStoredInOneCanonicalForm() {
        assertThat(AppointmentService.phone("+998 (90) 123-45-67")).isEqualTo("+998901234567");
        assertThat(AppointmentService.phone("90 123 45 67")).isEqualTo("901234567");
        assertThat(AppointmentService.phone(" ")).isNull();
        assertThatThrownBy(() -> AppointmentService.phone("12345")).isInstanceOf(InvalidFieldException.class);
    }
}
