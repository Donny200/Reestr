package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One browser of an anonymous guest, recognised by the SHA-256 of the secret in its HttpOnly cookie. */
@Entity
@Table(name = "help_guest_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HelpGuestSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long guestId;
    @Column(nullable = false, unique = true, length = 64)
    private String secretHash;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant lastSeenAt;
}
