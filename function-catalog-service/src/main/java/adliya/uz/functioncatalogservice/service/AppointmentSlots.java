package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.entity.AppointmentClosure;
import adliya.uz.functioncatalogservice.entity.AppointmentSchedule;
import adliya.uz.functioncatalogservice.entity.WorkingDay;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Slots of an organization's appointment calendar, computed from its weekly hours, slot length, closures, booking
 * window and the places already booked. All dates and times are local to the schedule's time zone; slot instants
 * are derived with {@link ZonedDateTime}, so days with a clock change are handled by the time-zone rules.
 */
public final class AppointmentSlots {

    private AppointmentSlots() {}

    public record Slot(LocalTime start, LocalTime end, Instant startsAt, Instant endsAt, int remaining) {}

    public record Day(LocalDate date, boolean open, List<Slot> slots) {}

    /** Today in the schedule's time zone. */
    public static LocalDate today(AppointmentSchedule schedule, Instant now) {
        return now.atZone(zone(schedule)).toLocalDate();
    }

    /** Last bookable date (inclusive): today plus the booking window minus one day. */
    public static LocalDate lastBookableDate(AppointmentSchedule schedule, Instant now) {
        return today(schedule, now).plusDays(Math.max(1, schedule.getBookingHorizonDays()) - 1L);
    }

    /**
     * Days from {@code from} (clamped to today) for {@code days} days, clamped to the booking window. Only slots with
     * a free place that start after the minimum notice are listed.
     */
    public static List<Day> days(AppointmentSchedule schedule, List<AppointmentClosure> closures, Map<Instant, Long> booked,
                                 Instant now, LocalDate from, int days) {
        LocalDate today = today(schedule, now);
        LocalDate first = from == null || from.isBefore(today) ? today : from;
        LocalDate last = lastBookableDate(schedule, now);
        List<Day> result = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last) && result.size() < days; date = date.plusDays(1)) {
            List<Slot> slots = slotsOf(schedule, closures, booked, now, date, true);
            result.add(new Day(date, isOpen(schedule, closures, date), slots));
        }
        return result;
    }

    /** The bookable slot starting exactly at {@code startsAt}, if it exists, is inside the booking window and has room. */
    public static Optional<Slot> bookable(AppointmentSchedule schedule, List<AppointmentClosure> closures, Map<Instant, Long> booked,
                                          Instant now, Instant startsAt) {
        LocalDate date = startsAt.atZone(zone(schedule)).toLocalDate();
        if (date.isBefore(today(schedule, now)) || date.isAfter(lastBookableDate(schedule, now))) {
            return Optional.empty();
        }
        return slotsOf(schedule, closures, booked, now, date, true).stream().filter(slot -> slot.startsAt().equals(startsAt)).findFirst();
    }

    static boolean isOpen(AppointmentSchedule schedule, List<AppointmentClosure> closures, LocalDate date) {
        return hours(schedule, date.getDayOfWeek()).isPresent()
                && closures.stream().noneMatch(closure -> closure.covers(date) && closure.wholeDay());
    }

    private static List<Slot> slotsOf(AppointmentSchedule schedule, List<AppointmentClosure> closures, Map<Instant, Long> booked,
                                      Instant now, LocalDate date, boolean onlyFree) {
        Optional<WorkingDay> hours = hours(schedule, date.getDayOfWeek());
        if (hours.isEmpty() || !isOpen(schedule, closures, date) || schedule.getSlotMinutes() <= 0) {
            return List.of();
        }
        ZoneId zone = zone(schedule);
        LocalTime opens = LocalTime.parse(hours.get().opens());
        LocalTime closes = LocalTime.parse(hours.get().closes());
        LocalTime breakStarts = hours.get().breakStarts() == null ? null : LocalTime.parse(hours.get().breakStarts());
        LocalTime breakEnds = hours.get().breakEnds() == null ? null : LocalTime.parse(hours.get().breakEnds());
        List<AppointmentClosure> partial = closures.stream().filter(closure -> closure.covers(date) && !closure.wholeDay()).toList();
        Instant earliest = now.plus(Duration.ofMinutes(schedule.getMinNoticeMinutes()));
        Duration length = Duration.ofMinutes(schedule.getSlotMinutes());
        List<Slot> slots = new ArrayList<>();
        for (LocalTime start = opens; ; start = start.plus(length)) {
            LocalTime end = start.plus(length);
            // Stop at closing time and when the time wraps past midnight.
            if (end.isAfter(closes) || !end.isAfter(start)) {
                break;
            }
            LocalTime slotStart = start;
            boolean inBreak = breakStarts != null && breakEnds != null && overlaps(slotStart, end, breakStarts, breakEnds);
            boolean unavailable = partial.stream().anyMatch(closure -> overlaps(slotStart, end, closure.getStartTime(), closure.getEndTime()));
            if (inBreak || unavailable) {
                continue;
            }
            Instant startsAt = ZonedDateTime.of(date, slotStart, zone).toInstant();
            Instant endsAt = ZonedDateTime.of(date, end, zone).toInstant();
            int remaining = (int) Math.max(0, schedule.getCapacity() - booked.getOrDefault(startsAt, 0L));
            if (startsAt.isBefore(earliest) || (onlyFree && remaining == 0)) {
                continue;
            }
            slots.add(new Slot(slotStart, end, startsAt, endsAt, remaining));
        }
        return slots;
    }

    private static boolean overlaps(LocalTime start, LocalTime end, LocalTime otherStart, LocalTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    private static Optional<WorkingDay> hours(AppointmentSchedule schedule, DayOfWeek day) {
        return schedule.weeklyHoursOrEmpty().stream().filter(item -> item.day() == day).findFirst();
    }

    public static ZoneId zone(AppointmentSchedule schedule) {
        return ZoneId.of(schedule.getTimeZone());
    }
}
