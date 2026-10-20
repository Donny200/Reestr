package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AppointmentProperties;
import adliya.uz.functioncatalogservice.dto.AppointmentDtos;
import adliya.uz.functioncatalogservice.dto.ReportingPeriod;
import adliya.uz.functioncatalogservice.entity.Appointment;
import adliya.uz.functioncatalogservice.entity.AppointmentClosure;
import adliya.uz.functioncatalogservice.entity.AppointmentSchedule;
import adliya.uz.functioncatalogservice.entity.AuditAction;
import adliya.uz.functioncatalogservice.entity.FunctionStatus;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.WorkingDay;
import adliya.uz.functioncatalogservice.exception.InvalidFieldException;
import adliya.uz.functioncatalogservice.exception.WorkflowConflictException;
import adliya.uz.functioncatalogservice.repository.AppointmentClosureRepository;
import adliya.uz.functioncatalogservice.repository.AppointmentRepository;
import adliya.uz.functioncatalogservice.repository.AppointmentScheduleRepository;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Appointment booking against calendars that organizations manage on this site. There is no external government
 * scheduling system behind it: a booking reserves a place in the organization's own published availability, which
 * its staff see and manage in the admin area.
 *
 * <p>Double booking is prevented on the server: bookings of one organization are serialized by locking its schedule
 * row, the requested slot is recomputed from the stored availability, and a partial unique index allows a seat of a
 * slot to be held by one BOOKED appointment only. Visitors manage their booking with the reference and a secret token
 * chosen by their browser (hash stored only). Staff endpoints are limited to the caller's organizations.</p>
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    public static final String VIEW = "APPOINTMENTS_VIEW";
    public static final String MANAGE = "APPOINTMENTS_MANAGE";
    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String NOT_FOUND = "Appointment not found";
    private static final int STAFF_LIST_LIMIT = 500;

    private final AppointmentScheduleRepository schedules;
    private final AppointmentClosureRepository closures;
    private final AppointmentRepository appointments;
    private final OrgFunctionRepository functions;
    private final IdentityOrganizationClient organizations;
    private final AppointmentRateLimiters limits;
    private final AppointmentProperties properties;
    private final CatalogAccess access;
    private final AuditWriter audit;
    private final TransactionTemplate transaction;

    // ---------------------------------------------------------------- public

    /** Free slots of the organization responsible for a published service; "not bookable" when it has no calendar. */
    public AppointmentDtos.Availability availability(Long serviceId, LocalDate from, Integer days) {
        OrgFunction service = publishedService(serviceId);
        Optional<AppointmentSchedule> schedule = schedules.findById(service.getOrganizationId()).filter(AppointmentSchedule::isEnabled);
        if (schedule.isEmpty()) {
            return AppointmentDtos.Availability.notBookable(service.getId(), service.getOrganizationId());
        }
        return availabilityOf(schedule.get(), service.getId(), from, days);
    }

    private AppointmentDtos.Availability availabilityOf(AppointmentSchedule schedule, Long serviceId, LocalDate from, Integer days) {
        int count = Math.max(1, Math.min(days == null ? 14 : days, properties.getMaxDaysPerRequest()));
        Instant now = Instant.now();
        List<AppointmentClosure> active = activeClosures(schedule, now);
        Map<Instant, Long> booked = bookedCounts(schedule, now);
        List<AppointmentDtos.Day> result = AppointmentSlots.days(schedule, active, booked, now, from, count).stream()
                .map(day -> new AppointmentDtos.Day(day.date(), day.open(), day.slots().stream()
                        .map(slot -> new AppointmentDtos.Slot(TIME.format(slot.start()), TIME.format(slot.end()), slot.startsAt())).toList()))
                .toList();
        return new AppointmentDtos.Availability(true, serviceId, schedule.getOrganizationId(), schedule.getTimeZone(),
                schedule.getSlotMinutes(), schedule.getVisitorNote(), AppointmentSlots.lastBookableDate(schedule, now), result);
    }

    /**
     * Reserves a place. Repeating the request with the same client id and token returns the same booking (safe retry);
     * the same client id with another token is refused.
     */
    public AppointmentDtos.VisitorAppointment book(AppointmentDtos.BookRequest request, String clientAddress) {
        String tokenHash = HelpAccessTokens.hash(request.accessToken());
        Optional<Appointment> repeated = appointments.findByClientRequestId(request.clientRequestId());
        if (repeated.isPresent()) {
            return visitorView(sameToken(repeated.get(), tokenHash));
        }
        if (request.website() != null && !request.website().isBlank()) {
            throw new InvalidFieldException("name", "This booking could not be sent");
        }
        limits.booking(clientAddress);
        String name = visitorName(request.name());
        String phone = phone(request.phone());
        String email = email(request.email());
        if (phone == null && email == null) {
            throw new InvalidFieldException("phone", "Enter a phone number or an e-mail address so the organization can reach you");
        }
        OrgFunction service = publishedService(request.serviceId());
        try {
            Appointment saved = transaction.execute(status -> {
                AppointmentSchedule schedule = lockedEnabledSchedule(service.getOrganizationId());
                Instant now = Instant.now();
                if (properties.getMaxUpcomingPerContact() > 0 && appointments.countUpcomingForContact(schedule.getOrganizationId(),
                        phone, email == null ? null : email.toLowerCase(Locale.ROOT), now) >= properties.getMaxUpcomingPerContact()) {
                    throw new WorkflowConflictException("You already have " + properties.getMaxUpcomingPerContact()
                            + " upcoming appointments with this organization; cancel one before booking another");
                }
                AppointmentSlots.Slot slot = freeSlot(schedule, request.startsAt(), now);
                Appointment appointment = Appointment.builder()
                        .reference(newReference()).organizationId(schedule.getOrganizationId())
                        .functionId(service.getId()).functionName(TextValues.truncate(service.getName(), 150))
                        .startsAt(slot.startsAt()).endsAt(slot.endsAt()).seat(freeSeat(schedule, slot.startsAt(), null))
                        .timeZone(schedule.getTimeZone()).status(Appointment.Status.BOOKED)
                        .visitorName(name).visitorPhone(phone).visitorEmail(email).language(TextValues.language(request.language()))
                        .accessTokenHash(tokenHash).clientRequestId(request.clientRequestId())
                        .createdAt(now).updatedAt(now).build();
                return appointments.saveAndFlush(appointment);
            });
            return visitorView(saved);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent retry with the same client id, or the last place of the slot was taken at the same moment.
            Optional<Appointment> stored = appointments.findByClientRequestId(request.clientRequestId());
            if (stored.isPresent()) {
                return visitorView(sameToken(stored.get(), tokenHash));
            }
            throw new WorkflowConflictException("This time was just booked by someone else; choose another time");
        }
    }

    public AppointmentDtos.VisitorAppointment view(String reference, String token, String clientAddress) {
        limits.lookup(clientAddress);
        return visitorView(authorized(reference, token));
    }

    public AppointmentDtos.VisitorAppointment cancel(String reference, String token, String clientAddress) {
        limits.lookup(clientAddress);
        Appointment current = authorized(reference, token);
        Appointment saved = transaction.execute(status -> {
            Appointment appointment = appointments.findById(current.getId()).orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
            if (appointment.getStatus() == Appointment.Status.CANCELLED) {
                return appointment;
            }
            if (appointment.getStatus() != Appointment.Status.BOOKED || !appointment.getStartsAt().isAfter(Instant.now())) {
                throw new WorkflowConflictException("Only upcoming appointments can be cancelled");
            }
            Instant now = Instant.now();
            appointment.setStatus(Appointment.Status.CANCELLED);
            appointment.setCancelledBy(Appointment.Party.VISITOR);
            appointment.setCancelledAt(now);
            appointment.setUpdatedAt(now);
            audit.appointment(appointment.getId(), AuditAction.APPOINTMENT_VISITOR_CHANGE, "Cancelled by the visitor", true);
            return appointments.saveAndFlush(appointment);
        });
        return visitorView(saved);
    }

    /** Moves an upcoming booking to another free slot of the same organization; the reference and token stay. */
    public AppointmentDtos.VisitorAppointment reschedule(String reference, String token, Instant startsAt, String clientAddress) {
        limits.lookup(clientAddress);
        Appointment current = authorized(reference, token);
        try {
            Appointment saved = transaction.execute(status -> {
                AppointmentSchedule schedule = lockedEnabledSchedule(current.getOrganizationId());
                Appointment appointment = appointments.findById(current.getId()).orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
                Instant now = Instant.now();
                if (appointment.getStatus() != Appointment.Status.BOOKED || !appointment.getStartsAt().isAfter(now)) {
                    throw new WorkflowConflictException("Only upcoming appointments can be moved");
                }
                if (appointment.getStartsAt().equals(startsAt)) {
                    return appointment;
                }
                AppointmentSlots.Slot slot = freeSlot(schedule, startsAt, now);
                Instant previous = appointment.getStartsAt();
                appointment.setStartsAt(slot.startsAt());
                appointment.setEndsAt(slot.endsAt());
                appointment.setSeat(freeSeat(schedule, slot.startsAt(), appointment.getId()));
                appointment.setTimeZone(schedule.getTimeZone());
                appointment.setUpdatedAt(now);
                audit.appointment(appointment.getId(), AuditAction.APPOINTMENT_VISITOR_CHANGE,
                        "Moved by the visitor from " + previous + " to " + slot.startsAt(), true);
                return appointments.saveAndFlush(appointment);
            });
            return visitorView(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new WorkflowConflictException("This time was just booked by someone else; choose another time");
        }
    }

    /** Free slots for moving an existing booking (same organization calendar). */
    public AppointmentDtos.Availability availabilityFor(String reference, String token, LocalDate from, Integer days, String clientAddress) {
        limits.lookup(clientAddress);
        Appointment appointment = authorized(reference, token);
        AppointmentSchedule schedule = schedules.findById(appointment.getOrganizationId()).filter(AppointmentSchedule::isEnabled)
                .orElse(null);
        return schedule == null ? AppointmentDtos.Availability.notBookable(appointment.getFunctionId(), appointment.getOrganizationId())
                : availabilityOf(schedule, appointment.getFunctionId(), from, days);
    }

    // ---------------------------------------------------------------- staff

    public AppointmentDtos.Schedule schedule(Long organizationId) {
        requireAnyPermission();
        access.requireOrganization(organizationId);
        Optional<AppointmentSchedule> stored = schedules.findById(organizationId);
        AppointmentSchedule schedule = stored.orElseGet(() -> defaults(organizationId));
        List<AppointmentDtos.Closure> list = closures.findAllByOrganizationIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(
                organizationId, AppointmentSlots.today(schedule, Instant.now()).minusDays(1)).stream().map(AppointmentDtos.Closure::of).toList();
        return new AppointmentDtos.Schedule(organizationId, stored.isPresent(), schedule.isEnabled(), schedule.getTimeZone(),
                schedule.getSlotMinutes(), schedule.getCapacity(), schedule.getBookingHorizonDays(), schedule.getMinNoticeMinutes(),
                schedule.getVisitorNote(), schedule.weeklyHoursOrEmpty(), list, stored.map(AppointmentSchedule::getUpdatedAt).orElse(null));
    }

    public AppointmentDtos.Schedule updateSchedule(Long organizationId, AppointmentDtos.ScheduleRequest request) {
        access.requirePermission(MANAGE);
        access.requireOrganization(organizationId);
        organizations.requireExisting(organizationId);
        String zone = zone(request.timeZone());
        List<WorkingDay> hours = weeklyHours(request.weeklyHours(), request.slotMinutes());
        if (Boolean.TRUE.equals(request.enabled()) && hours.isEmpty()) {
            throw new InvalidFieldException("weeklyHours", "Add opening hours for at least one day before turning online booking on");
        }
        transaction.executeWithoutResult(status -> {
            AppointmentSchedule schedule = schedules.findById(organizationId).orElseGet(() -> defaults(organizationId));
            schedule.setEnabled(request.enabled());
            schedule.setTimeZone(zone);
            schedule.setSlotMinutes(request.slotMinutes());
            schedule.setCapacity(request.capacity());
            schedule.setBookingHorizonDays(request.bookingHorizonDays());
            schedule.setMinNoticeMinutes(request.minNoticeMinutes());
            schedule.setVisitorNote(TextValues.blankToNull(request.visitorNote()));
            schedule.setWeeklyHours(hours);
            schedule.setUpdatedAt(Instant.now());
            schedule.setUpdatedByUserId(access.principal().userId());
            schedules.saveAndFlush(schedule);
            audit.schedule(organizationId, AuditAction.APPOINTMENT_SCHEDULE_UPDATE, "Online booking " + (request.enabled() ? "on" : "off")
                    + "; " + request.slotMinutes() + "-minute slots, " + request.capacity() + " in parallel, " + hours.size()
                    + " working days, time zone " + zone + "; existing bookings unchanged");
        });
        return schedule(organizationId);
    }

    public AppointmentDtos.Schedule addClosure(Long organizationId, AppointmentDtos.ClosureRequest request) {
        access.requirePermission(MANAGE);
        access.requireOrganization(organizationId);
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidFieldException("endDate", "The last day must not be before the first day");
        }
        if (request.endDate().isAfter(request.startDate().plusDays(366))) {
            throw new InvalidFieldException("endDate", "A closure may cover at most one year");
        }
        LocalTime start = time(request.startTime());
        LocalTime end = time(request.endTime());
        if ((start == null) != (end == null)) {
            throw new InvalidFieldException("endTime", "Enter both times, or none for whole days");
        }
        if (start != null && !end.isAfter(start)) {
            throw new InvalidFieldException("endTime", "The end time must be after the start time");
        }
        transaction.executeWithoutResult(status -> {
            AppointmentClosure closure = closures.saveAndFlush(AppointmentClosure.builder().organizationId(organizationId)
                    .kind(request.kind()).startDate(request.startDate()).endDate(request.endDate()).startTime(start).endTime(end)
                    .note(TextValues.blankToNull(request.note())).createdAt(Instant.now()).createdByUserId(access.principal().userId()).build());
            audit.schedule(organizationId, AuditAction.APPOINTMENT_CLOSURE_ADD, request.kind() + " #" + closure.getId() + ": "
                    + request.startDate() + " - " + request.endDate() + (start == null ? " (whole days)" : " " + start + "-" + end)
                    + "; existing bookings unchanged");
        });
        return schedule(organizationId);
    }

    public AppointmentDtos.Schedule removeClosure(Long organizationId, Long closureId) {
        access.requirePermission(MANAGE);
        access.requireOrganization(organizationId);
        transaction.executeWithoutResult(status -> {
            AppointmentClosure closure = closures.findById(closureId).filter(found -> organizationId.equals(found.getOrganizationId()))
                    .orElseThrow(() -> new NoSuchElementException("Closure not found"));
            closures.delete(closure);
            audit.schedule(organizationId, AuditAction.APPOINTMENT_CLOSURE_REMOVE, closure.getKind() + " #" + closureId + " removed");
        });
        return schedule(organizationId);
    }

    public record StaffQuery(Long organizationId, LocalDate from, LocalDate to, Appointment.Status status) {}

    /** Bookings of the caller's organizations in a date range (default: from today, 31 days); earliest first. */
    public List<AppointmentDtos.StaffAppointment> list(StaffQuery query) {
        requireAnyPermission();
        OrganizationScope scope = access.scope(query.organizationId());
        if (scope.empty()) {
            return List.of();
        }
        ZoneId zone = query.organizationId() == null ? ZoneId.of(properties.getDefaultTimeZone())
                : schedules.findById(query.organizationId()).map(AppointmentSlots::zone).orElse(ZoneId.of(properties.getDefaultTimeZone()));
        LocalDate from = query.from() == null ? LocalDate.now(zone) : query.from();
        LocalDate to = query.to() == null ? from.plusDays(30) : query.to();
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        ReportingPeriod days = new ReportingPeriod(from, to);
        Instant start = days.start(zone);
        Instant end = days.endExclusive(zone);
        Specification<Appointment> specification = (root, criteria, builder) -> builder.and(
                builder.greaterThanOrEqualTo(root.get("startsAt"), start), builder.lessThan(root.get("startsAt"), end));
        specification = specification.and(ScopeSpecifications.inScope(scope));
        if (query.status() != null) {
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("status"), query.status()));
        }
        boolean contacts = access.hasPermission(MANAGE);
        return appointments.findAll(specification, PageRequest.of(0, STAFF_LIST_LIMIT, Sort.by("startsAt", "id"))).getContent()
                .stream().map(appointment -> staffView(appointment, contacts)).toList();
    }

    /** Staff outcome of a booking: cancelled by the organization, attended or no-show. */
    public AppointmentDtos.StaffAppointment changeStatus(Long id, Appointment.Status target) {
        access.requirePermission(MANAGE);
        Appointment saved = transaction.execute(status -> {
            Appointment appointment = appointments.findById(id).orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
            access.requireOrganization(appointment.getOrganizationId());
            Appointment.Status previous = appointment.getStatus();
            if (previous == target) {
                return appointment;
            }
            Instant now = Instant.now();
            boolean allowed = switch (target) {
                case CANCELLED -> previous == Appointment.Status.BOOKED;
                case ATTENDED, NO_SHOW -> EnumSet.of(Appointment.Status.BOOKED, Appointment.Status.ATTENDED, Appointment.Status.NO_SHOW)
                        .contains(previous) && !appointment.getStartsAt().isAfter(now);
                case BOOKED -> false;
            };
            if (!allowed) {
                throw new WorkflowConflictException(target == Appointment.Status.CANCELLED ? "Only booked appointments can be cancelled"
                        : target == Appointment.Status.BOOKED ? "A cancelled or completed appointment cannot be booked again; the visitor books a new time"
                        : "Mark attendance once the appointment time has started");
            }
            appointment.setStatus(target);
            if (target == Appointment.Status.CANCELLED) {
                appointment.setCancelledBy(Appointment.Party.STAFF);
                appointment.setCancelledAt(now);
            }
            appointment.setHandledByUserId(access.principal().userId());
            appointment.setUpdatedAt(now);
            audit.appointment(appointment.getId(), AuditAction.APPOINTMENT_STATUS_CHANGE, previous + " -> " + target, false);
            return appointments.saveAndFlush(appointment);
        });
        return staffView(saved, true);
    }

    // ---------------------------------------------------------------- helpers

    private OrgFunction publishedService(Long serviceId) {
        OrgFunction service = functions.requirePublished(serviceId);
        if (service.getOrganizationId() == null) {
            throw new NoSuchElementException("Service not found: " + serviceId);
        }
        return service;
    }

    private AppointmentSchedule lockedEnabledSchedule(Long organizationId) {
        return schedules.lock(organizationId).filter(AppointmentSchedule::isEnabled)
                .orElseThrow(() -> new WorkflowConflictException("This organization does not take online appointments at the moment"));
    }

    private AppointmentSlots.Slot freeSlot(AppointmentSchedule schedule, Instant startsAt, Instant now) {
        return AppointmentSlots.bookable(schedule, activeClosures(schedule, now), bookedCounts(schedule, now), now, startsAt)
                .orElseThrow(() -> new WorkflowConflictException("This time is not available any more; choose another time"));
    }

    /** First place of the slot not held by another booked appointment (the database index enforces it too). */
    private int freeSeat(AppointmentSchedule schedule, Instant startsAt, Long ignoreAppointmentId) {
        Set<Integer> taken = new java.util.HashSet<>(appointments.bookedSeats(schedule.getOrganizationId(), startsAt));
        if (ignoreAppointmentId != null) {
            appointments.findById(ignoreAppointmentId).filter(own -> own.getStartsAt().equals(startsAt)).ifPresent(own -> taken.remove(own.getSeat()));
        }
        for (int seat = 1; seat <= schedule.getCapacity(); seat++) {
            if (!taken.contains(seat)) {
                return seat;
            }
        }
        throw new WorkflowConflictException("This time was just booked by someone else; choose another time");
    }

    private List<AppointmentClosure> activeClosures(AppointmentSchedule schedule, Instant now) {
        return closures.findAllByOrganizationIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(
                schedule.getOrganizationId(), AppointmentSlots.today(schedule, now));
    }

    private Map<Instant, Long> bookedCounts(AppointmentSchedule schedule, Instant now) {
        Instant from = AppointmentSlots.today(schedule, now).atStartOfDay(AppointmentSlots.zone(schedule)).toInstant();
        Instant to = AppointmentSlots.lastBookableDate(schedule, now).plusDays(2).atStartOfDay(AppointmentSlots.zone(schedule)).toInstant();
        Map<Instant, Long> result = new HashMap<>();
        for (Object[] row : appointments.bookedCounts(schedule.getOrganizationId(), from, to)) {
            result.put((Instant) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private Appointment authorized(String reference, String token) {
        String canonical = reference == null ? "" : reference.trim().toUpperCase(Locale.ROOT);
        if (token == null || !token.matches(AppointmentDtos.TOKEN_PATTERN)) {
            throw new NoSuchElementException(NOT_FOUND);
        }
        Appointment appointment = appointments.findByReference(canonical).orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
        if (!sameHash(appointment, HelpAccessTokens.hash(token))) {
            throw new NoSuchElementException(NOT_FOUND);
        }
        return appointment;
    }

    private static Appointment sameToken(Appointment appointment, String tokenHash) {
        if (!sameHash(appointment, tokenHash)) {
            throw new WorkflowConflictException("This booking request was already used");
        }
        return appointment;
    }

    /** Constant-time comparison, so response timing reveals nothing about the stored hash. */
    private static boolean sameHash(Appointment appointment, String tokenHash) {
        return MessageDigest.isEqual(appointment.getAccessTokenHash().getBytes(StandardCharsets.US_ASCII),
                tokenHash.getBytes(StandardCharsets.US_ASCII));
    }

    private AppointmentDtos.VisitorAppointment visitorView(Appointment appointment) {
        ZonedDateTime start = appointment.getStartsAt().atZone(ZoneId.of(appointment.getTimeZone()));
        ZonedDateTime end = appointment.getEndsAt().atZone(ZoneId.of(appointment.getTimeZone()));
        Optional<OrgFunction> service = functions.findByIdAndStatus(appointment.getFunctionId(), FunctionStatus.PUBLISHED);
        Optional<IdentityOrganizationClient.PublicOrganization> organization = organizations.findPublic(appointment.getOrganizationId());
        Optional<AppointmentSchedule> schedule = schedules.findById(appointment.getOrganizationId());
        boolean upcoming = appointment.getStatus() == Appointment.Status.BOOKED && appointment.getStartsAt().isAfter(Instant.now());
        return new AppointmentDtos.VisitorAppointment(appointment.getReference(), appointment.getStatus(), appointment.getCancelledBy(),
                appointment.getFunctionId(), service.map(OrgFunction::getName).orElse(appointment.getFunctionName()),
                service.map(OrgFunction::getNameTranslations).orElse(Map.of()),
                appointment.getOrganizationId(), organization.map(IdentityOrganizationClient.PublicOrganization::name).orElse(null),
                organization.map(IdentityOrganizationClient.PublicOrganization::nameTranslations).orElse(null),
                organization.map(IdentityOrganizationClient.PublicOrganization::contact).orElse(null),
                appointment.getStartsAt(), appointment.getEndsAt(), appointment.getTimeZone(), start.toLocalDate(),
                TIME.format(start), TIME.format(end), appointment.getVisitorName(), appointment.getVisitorPhone(),
                appointment.getVisitorEmail(), upcoming, upcoming && schedule.map(AppointmentSchedule::isEnabled).orElse(false),
                schedule.map(AppointmentSchedule::getVisitorNote).orElse(null), appointment.getCreatedAt());
    }

    private static AppointmentDtos.StaffAppointment staffView(Appointment appointment, boolean contacts) {
        ZonedDateTime start = appointment.getStartsAt().atZone(ZoneId.of(appointment.getTimeZone()));
        ZonedDateTime end = appointment.getEndsAt().atZone(ZoneId.of(appointment.getTimeZone()));
        return new AppointmentDtos.StaffAppointment(appointment.getId(), appointment.getReference(), appointment.getStatus(),
                appointment.getCancelledBy(), appointment.getFunctionId(), appointment.getFunctionName(), appointment.getOrganizationId(),
                appointment.getStartsAt(), appointment.getEndsAt(), appointment.getTimeZone(), start.toLocalDate(), TIME.format(start),
                TIME.format(end), appointment.getSeat(), appointment.getVisitorName(),
                contacts ? appointment.getVisitorPhone() : null, contacts ? appointment.getVisitorEmail() : null,
                appointment.getContactRemovedAt() != null, appointment.getCreatedAt(), appointment.getUpdatedAt());
    }

    private void requireAnyPermission() {
        if (!access.hasPermission(VIEW) && !access.hasPermission(MANAGE)) {
            throw new org.springframework.security.access.AccessDeniedException("Required permission: " + VIEW);
        }
    }

    private AppointmentSchedule defaults(Long organizationId) {
        return AppointmentSchedule.builder().organizationId(organizationId).enabled(false).timeZone(properties.getDefaultTimeZone())
                .slotMinutes(30).capacity(1).bookingHorizonDays(30).minNoticeMinutes(120).weeklyHours(List.of())
                .updatedAt(Instant.now()).build();
    }

    private static String zone(String value) {
        try {
            String trimmed = value.trim();
            if (!ZoneId.getAvailableZoneIds().contains(trimmed)) {
                throw new DateTimeException(trimmed);
            }
            return ZoneId.of(trimmed).getId();
        } catch (DateTimeException exception) {
            throw new InvalidFieldException("timeZone", "Choose a time zone such as Asia/Tashkent");
        }
    }

    /** One entry per weekday, opening before closing, a break (both times) inside the opening hours, room for one slot. */
    static List<WorkingDay> weeklyHours(List<WorkingDay> requested, int slotMinutes) {
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        List<WorkingDay> result = new ArrayList<>();
        for (WorkingDay day : requested) {
            if (!seen.add(day.day())) {
                throw new InvalidFieldException("weeklyHours", "Each weekday may appear only once");
            }
            LocalTime opens = LocalTime.parse(day.opens());
            LocalTime closes = LocalTime.parse(day.closes());
            if (!closes.isAfter(opens)) {
                throw new InvalidFieldException("weeklyHours", day.day() + ": closing time must be after opening time");
            }
            if (Duration.between(opens, closes).toMinutes() < slotMinutes) {
                throw new InvalidFieldException("weeklyHours", day.day() + ": the opening hours are shorter than one appointment");
            }
            String breakStarts = TextValues.blankToNull(day.breakStarts());
            String breakEnds = TextValues.blankToNull(day.breakEnds());
            if ((breakStarts == null) != (breakEnds == null)) {
                throw new InvalidFieldException("weeklyHours", day.day() + ": enter both break times, or none");
            }
            if (breakStarts != null) {
                LocalTime from = LocalTime.parse(breakStarts);
                LocalTime to = LocalTime.parse(breakEnds);
                if (!to.isAfter(from) || from.isBefore(opens) || to.isAfter(closes)) {
                    throw new InvalidFieldException("weeklyHours", day.day() + ": the break must lie within the opening hours");
                }
            }
            result.add(new WorkingDay(day.day(), day.opens(), day.closes(), breakStarts, breakEnds));
        }
        result.sort(java.util.Comparator.comparing(WorkingDay::day));
        return result;
    }

    private static LocalTime time(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value.trim());
    }

    private static String visitorName(String raw) {
        String value = VisitorText.normalize(raw);
        if (!VisitorText.meaningful(value, 2) || value.length() > 100) {
            throw new InvalidFieldException("name", "Enter your name (2 to 100 characters)");
        }
        if (VisitorText.containsLongNumber(value)) {
            throw new InvalidFieldException("name", "Enter only your name; identification numbers are not needed");
        }
        return value.replace('\n', ' ');
    }

    /** Digits with an optional leading "+", so the same number written differently counts once. */
    static String phone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 7 || digits.length() > 15) {
            throw new InvalidFieldException("phone", "Enter a phone number with 7 to 15 digits");
        }
        return (value.startsWith("+") ? "+" : "") + digits;
    }

    private static String email(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!value.matches(AppointmentDtos.EMAIL_PATTERN)) {
            throw new InvalidFieldException("email", "Enter a valid e-mail address");
        }
        return value;
    }

    private String newReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder reference = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                if (index == 4) {
                    reference.append('-');
                }
                reference.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
            }
            if (!appointments.existsByReference(reference.toString())) {
                return reference.toString();
            }
        }
        throw new IllegalStateException("Could not allocate a booking reference");
    }
}
