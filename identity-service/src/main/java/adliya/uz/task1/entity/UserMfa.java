package adliya.uz.task1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * TOTP enrollment of one user. The shared secret is stored only encrypted with the configured key and is never
 * returned, logged or audited; {@code confirmedAt} is null while the enrollment is pending a first valid code.
 */
@Entity
@Table(name = "user_mfa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMfa {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "secret_ciphertext", nullable = false, length = 512)
    private String secretCiphertext;

    private Instant confirmedAt;

    /** Time step of the last accepted code; a code is accepted at most once. */
    private Long lastUsedStep;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
