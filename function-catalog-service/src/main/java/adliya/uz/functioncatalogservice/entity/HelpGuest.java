package adliya.uz.functioncatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A visitor's support identity: a signed-in account (userId) or an anonymous browser reached through its sessions. */
@Entity
@Table(name = "help_guests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HelpGuest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private Long userId;
    /** SHA-256 of the optional recovery code; the code itself is shown to the visitor once and never stored. */
    @Column(unique = true, length = 64)
    private String recoveryCodeHash;
    /** Optional, given by the visitor for support follow-up only. */
    @Column(length = 254)
    private String contactEmail;
    @Column(nullable = false)
    @Builder.Default
    private boolean notifyByEmail = false;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant lastSeenAt;
    @Version
    private Long version;
}
