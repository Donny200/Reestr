package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A holiday, closed date or unavailable period of an organization's appointment calendar (local dates and times of
 * its schedule). Without times every day of the range is closed; with times only that part of each day.
 */
@Entity
@Table(name = "appointment_closures")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppointmentClosure {
    public enum Kind { HOLIDAY, CLOSED, UNAVAILABLE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long organizationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Kind kind;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    @Column(length = 200)
    private String note;
    @Column(nullable = false)
    private Instant createdAt;
    private Long createdByUserId;

    public boolean wholeDay() {
        return startTime == null || endTime == null;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
