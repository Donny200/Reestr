package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** Availability one organization publishes for visitor appointments; managed by its own staff. */
@Entity
@Table(name = "appointment_schedules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppointmentSchedule {
    @Id
    private Long organizationId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 64)
    private String timeZone;
    /** Length of one appointment slot. */
    @Column(nullable = false)
    private int slotMinutes;
    /** Visitors that can be received in parallel in one slot (desks). */
    @Column(nullable = false)
    private int capacity;
    /** How many days ahead (today included) visitors may book. */
    @Column(nullable = false)
    private int bookingHorizonDays;
    /** Bookings must start at least this long after now. */
    @Column(nullable = false)
    private int minNoticeMinutes;
    /** Optional public note shown before booking (e.g. "Please arrive 10 minutes early"). */
    @Column(length = 500)
    private String visitorNote;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private List<WorkingDay> weeklyHours;
    @Column(nullable = false)
    private Instant updatedAt;
    private Long updatedByUserId;
    @Version
    private Long version;

    public List<WorkingDay> weeklyHoursOrEmpty() {
        return weeklyHours == null ? List.of() : weeklyHours;
    }
}
