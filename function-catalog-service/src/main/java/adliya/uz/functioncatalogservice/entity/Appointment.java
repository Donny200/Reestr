package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One visitor appointment in an organization's calendar on this site. The visitor manages it with the booking
 * reference plus a secret access token chosen by their browser (only its hash is stored); there is no account.
 */
@Entity
@Table(name = "appointments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Appointment {
    public enum Status { BOOKED, CANCELLED, ATTENDED, NO_SHOW }
    public enum Party { VISITOR, STAFF }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 12, unique = true)
    private String reference;
    @Column(nullable = false)
    private Long organizationId;
    @Column(nullable = false)
    private Long functionId;
    /** Name of the service when it was booked, shown if the card is later unpublished. */
    @Column(nullable = false, length = 150)
    private String functionName;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    /** Which of the slot's parallel places (1..capacity) this booking holds; unique among BOOKED rows. */
    @Column(nullable = false)
    private int seat;
    @Column(nullable = false, length = 64)
    private String timeZone;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Party cancelledBy;
    @Column(length = 100)
    private String visitorName;
    @Column(length = 30)
    private String visitorPhone;
    @Column(length = 254)
    private String visitorEmail;
    @Column(length = 35)
    private String language;
    @Column(nullable = false, length = 64)
    private String accessTokenHash;
    @Column(nullable = false, length = 64, unique = true)
    private String clientRequestId;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant cancelledAt;
    private Instant contactRemovedAt;
    private Long handledByUserId;
    @Version
    private Long version;
}
