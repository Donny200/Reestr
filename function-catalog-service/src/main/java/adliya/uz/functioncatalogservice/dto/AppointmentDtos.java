package adliya.uz.functioncatalogservice.dto;

import adliya.uz.functioncatalogservice.entity.Appointment;
import adliya.uz.functioncatalogservice.entity.AppointmentClosure;
import adliya.uz.functioncatalogservice.entity.TranslatedText;
import adliya.uz.functioncatalogservice.entity.WorkingDay;
import adliya.uz.functioncatalogservice.service.IdentityOrganizationClient;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AppointmentDtos {

    private AppointmentDtos() {}

    public static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{43}";
    public static final String PHONE_PATTERN = "^\\s*$|^\\+?[0-9][0-9 ()-]{5,22}[0-9]$";
    public static final String EMAIL_PATTERN = "^\\s*$|^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$";

    // ---------------------------------------------------------------- public

    /** Whether and when a published service can be booked, with the free slots of the requested days. */
    public record Availability(boolean bookable, Long serviceId, Long organizationId, String timeZone, Integer slotMinutes,
                               String visitorNote, LocalDate lastBookableDate, List<Day> days) {
        public static Availability notBookable(Long serviceId, Long organizationId) {
            return new Availability(false, serviceId, organizationId, null, null, null, null, List.of());
        }
    }

    public record Day(LocalDate date, boolean open, List<Slot> slots) {}

    /** {@code start}/{@code end} are local times of the organization ("09:30"); {@code startsAt} is the instant to book. */
    public record Slot(String start, String end, Instant startsAt) {}

    public record BookRequest(
            @NotNull @Positive Long serviceId,
            @NotNull Instant startsAt,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 30) @Pattern(regexp = PHONE_PATTERN, message = "Enter a phone number with digits only, e.g. +998 90 123 45 67") String phone,
            @Size(max = 254) @Pattern(regexp = EMAIL_PATTERN, message = "Enter a valid e-mail address") String email,
            /** Secret chosen by the visitor's browser (256 bits, URL-safe Base64); only its hash is stored. */
            @NotBlank @Pattern(regexp = TOKEN_PATTERN, message = "Invalid access token") String accessToken,
            /** Idempotency key: repeating a request with the same key and token returns the same booking. */
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId,
            @Size(max = 35) String language,
            /** Honeypot; must stay empty. */
            @Size(max = 200) String website
    ) {}

    public record RescheduleRequest(@NotNull Instant startsAt) {}

    /** What the visitor (holder of the reference and token) sees about their appointment. */
    public record VisitorAppointment(
            String reference, Appointment.Status status, Appointment.Party cancelledBy,
            Long serviceId, String serviceName, Map<String, TranslatedText> serviceNameTranslations,
            Long organizationId, String organizationName, Map<String, TranslatedText> organizationNameTranslations,
            IdentityOrganizationClient.Contact organizationContact,
            Instant startsAt, Instant endsAt, String timeZone, LocalDate localDate, String localStart, String localEnd,
            String visitorName, String visitorPhone, String visitorEmail,
            boolean canCancel, boolean canReschedule, String visitorNote, Instant createdAt) {}

    // ---------------------------------------------------------------- staff

    public record ScheduleRequest(
            @NotNull Boolean enabled,
            @NotBlank @Size(max = 64) String timeZone,
            @NotNull @Min(5) @Max(240) Integer slotMinutes,
            @NotNull @Min(1) @Max(50) Integer capacity,
            @NotNull @Min(1) @Max(365) Integer bookingHorizonDays,
            @NotNull @Min(0) @Max(20160) Integer minNoticeMinutes,
            @Size(max = 500) String visitorNote,
            @NotNull @Size(max = 7) List<@Valid @NotNull WorkingDay> weeklyHours
    ) {}

    public record ClosureRequest(
            @NotNull AppointmentClosure.Kind kind,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Pattern(regexp = "^$|" + WorkingDay.TIME) String startTime,
            @Pattern(regexp = "^$|" + WorkingDay.TIME) String endTime,
            @Size(max = 200) String note
    ) {}

    public record Closure(Long id, AppointmentClosure.Kind kind, LocalDate startDate, LocalDate endDate, String startTime,
                          String endTime, String note, Instant createdAt) {
        public static Closure of(AppointmentClosure closure) {
            return new Closure(closure.getId(), closure.getKind(), closure.getStartDate(), closure.getEndDate(),
                    closure.getStartTime() == null ? null : closure.getStartTime().toString(),
                    closure.getEndTime() == null ? null : closure.getEndTime().toString(), closure.getNote(), closure.getCreatedAt());
        }
    }

    /** An organization's calendar settings; {@code configured} is false while the defaults have never been saved. */
    public record Schedule(Long organizationId, boolean configured, boolean enabled, String timeZone, int slotMinutes, int capacity,
                           int bookingHorizonDays, int minNoticeMinutes, String visitorNote, List<WorkingDay> weeklyHours,
                           List<Closure> closures, Instant updatedAt) {}

    public record StatusRequest(@NotNull Appointment.Status status) {}

    /** A booking as organization staff see it; contact details only for staff who may manage appointments. */
    public record StaffAppointment(
            Long id, String reference, Appointment.Status status, Appointment.Party cancelledBy,
            Long serviceId, String serviceName, Long organizationId,
            Instant startsAt, Instant endsAt, String timeZone, LocalDate localDate, String localStart, String localEnd, int seat,
            String visitorName,
            @JsonInclude(JsonInclude.Include.NON_NULL) String visitorPhone,
            @JsonInclude(JsonInclude.Include.NON_NULL) String visitorEmail,
            boolean contactRemoved, Instant createdAt, Instant updatedAt) {}
}
