package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.HelpGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface HelpGuestRepository extends JpaRepository<HelpGuest, Long> {
    Optional<HelpGuest> findByUserId(Long userId);

    Optional<HelpGuest> findByRecoveryCodeHash(String recoveryCodeHash);

    @Modifying
    @Query("update HelpGuest g set g.lastSeenAt = :now where g.id = :id and g.lastSeenAt < :now")
    int touch(Long id, Instant now);

    /** Contact details are dropped once a guest has been inactive for the retention period. */
    @Modifying
    @Query("update HelpGuest g set g.contactEmail = null, g.notifyByEmail = false "
            + "where g.contactEmail is not null and g.lastSeenAt < :cutoff")
    int clearIdleContacts(Instant cutoff);
}
