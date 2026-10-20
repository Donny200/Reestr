package adliya.uz.functioncatalogservice.controller;

import adliya.uz.functioncatalogservice.dto.AppointmentDtos;
import adliya.uz.functioncatalogservice.entity.Appointment;
import adliya.uz.functioncatalogservice.service.AppointmentService;
import adliya.uz.functioncatalogservice.service.ClientAddressResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Visitor appointments in calendars that organizations manage on this site (no external "
        + "government scheduling system). Visitors book without an account and manage the booking with its reference and the "
        + "secret token their browser chose (header X-Appointment-Token). Staff endpoints are limited to their organizations.")
public class AppointmentController {

    static final String TOKEN_HEADER = "X-Appointment-Token";
    static final String REFERENCE = "{reference:[A-Za-z0-9]{4}-[A-Za-z0-9]{4}}";
    static final String VIEW = "hasAnyAuthority('APPOINTMENTS_VIEW', 'APPOINTMENTS_MANAGE')";
    static final String MANAGE = "hasAuthority('APPOINTMENTS_MANAGE')";

    private final AppointmentService appointments;
    private final ClientAddressResolver clientAddresses;

    // ---------------------------------------------------------------- visitor

    @GetMapping("/services/{serviceId}/availability")
    @Operation(summary = "Free appointment times for a published service", description = "bookable=false when the responsible "
            + "organization does not take online appointments. Dates and times are local to the organization's time zone.")
    public ResponseEntity<AppointmentDtos.Availability> availability(@PathVariable Long serviceId,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                     @RequestParam(required = false) Integer days) {
        return noStore(appointments.availability(serviceId, from, days));
    }

    @PostMapping
    @Operation(summary = "Book an appointment", description = "Reserves one place of a free slot; 409 when the time was just taken. "
            + "Idempotent by clientRequestId with the same accessToken.")
    public ResponseEntity<AppointmentDtos.VisitorAppointment> book(@Valid @RequestBody AppointmentDtos.BookRequest body,
                                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(appointments.book(body, clientAddresses.resolve(request)));
    }

    @GetMapping("/" + REFERENCE)
    @Operation(summary = "My appointment", description = "Requires the booking's access token; 404 for an unknown reference or a wrong token.")
    public ResponseEntity<AppointmentDtos.VisitorAppointment> view(@PathVariable String reference,
                                                                   @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                                   HttpServletRequest request) {
        return noStore(appointments.view(reference, token, clientAddresses.resolve(request)));
    }

    @GetMapping("/" + REFERENCE + "/availability")
    @Operation(summary = "Free times for moving my appointment")
    public ResponseEntity<AppointmentDtos.Availability> rescheduleOptions(@PathVariable String reference,
                                                                          @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                          @RequestParam(required = false) Integer days,
                                                                          HttpServletRequest request) {
        return noStore(appointments.availabilityFor(reference, token, from, days, clientAddresses.resolve(request)));
    }

    @PostMapping("/" + REFERENCE + "/cancel")
    @Operation(summary = "Cancel my appointment", description = "Upcoming bookings only; repeating it is harmless.")
    public ResponseEntity<AppointmentDtos.VisitorAppointment> cancel(@PathVariable String reference,
                                                                     @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                                     HttpServletRequest request) {
        return noStore(appointments.cancel(reference, token, clientAddresses.resolve(request)));
    }

    @PostMapping("/" + REFERENCE + "/reschedule")
    @Operation(summary = "Move my appointment to another free time", description = "Reference and token stay the same; 409 when the new time was just taken.")
    public ResponseEntity<AppointmentDtos.VisitorAppointment> reschedule(@PathVariable String reference,
                                                                         @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                                         @Valid @RequestBody AppointmentDtos.RescheduleRequest body,
                                                                         HttpServletRequest request) {
        return noStore(appointments.reschedule(reference, token, body.startsAt(), clientAddresses.resolve(request)));
    }

    // ---------------------------------------------------------------- staff

    @GetMapping("/staff")
    @PreAuthorize(VIEW)
    @Operation(summary = "Bookings of my organizations", description = "Date range in the organization's time zone (default: today and "
            + "the next 30 days). Visitor phone and e-mail only with APPOINTMENTS_MANAGE.")
    public List<AppointmentDtos.StaffAppointment> list(@RequestParam(required = false) Long organizationId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(required = false) Appointment.Status status) {
        return appointments.list(new AppointmentService.StaffQuery(organizationId, from, to, status));
    }

    @PutMapping("/staff/{id}/status")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Cancel a booking, or mark it attended / no-show")
    public AppointmentDtos.StaffAppointment status(@PathVariable Long id, @Valid @RequestBody AppointmentDtos.StatusRequest body) {
        return appointments.changeStatus(id, body.status());
    }

    @GetMapping("/schedules/{organizationId}")
    @PreAuthorize(VIEW)
    @Operation(summary = "Availability settings and closures of an organization")
    public AppointmentDtos.Schedule schedule(@PathVariable Long organizationId) {
        return appointments.schedule(organizationId);
    }

    @PutMapping("/schedules/{organizationId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change working hours, slot length, parallel places, booking window and whether online booking is on",
            description = "Existing bookings are never moved or cancelled by a change.")
    public AppointmentDtos.Schedule updateSchedule(@PathVariable Long organizationId, @Valid @RequestBody AppointmentDtos.ScheduleRequest body) {
        return appointments.updateSchedule(organizationId, body);
    }

    @PostMapping("/schedules/{organizationId}/closures")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Add a holiday, closed date or unavailable period")
    public AppointmentDtos.Schedule addClosure(@PathVariable Long organizationId, @Valid @RequestBody AppointmentDtos.ClosureRequest body) {
        return appointments.addClosure(organizationId, body);
    }

    @DeleteMapping("/schedules/{organizationId}/closures/{closureId}")
    @PreAuthorize(MANAGE)
    public AppointmentDtos.Schedule removeClosure(@PathVariable Long organizationId, @PathVariable Long closureId) {
        return appointments.removeClosure(organizationId, closureId);
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
