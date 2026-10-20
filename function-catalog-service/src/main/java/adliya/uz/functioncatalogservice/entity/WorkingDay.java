package adliya.uz.functioncatalogservice.entity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.DayOfWeek;

/**
 * Opening hours of one weekday in the schedule's time zone ("HH:mm"), with an optional break during which no
 * appointment may run (e.g. lunch).
 */
public record WorkingDay(
        @NotNull DayOfWeek day,
        @NotNull @Pattern(regexp = TIME) String opens,
        @NotNull @Pattern(regexp = TIME) String closes,
        @Pattern(regexp = TIME) String breakStarts,
        @Pattern(regexp = TIME) String breakEnds
) {
    public static final String TIME = "([01]\\d|2[0-3]):[0-5]\\d";
}
