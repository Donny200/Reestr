package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.HelpGuestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface HelpGuestSessionRepository extends JpaRepository<HelpGuestSession, Long> {
    Optional<HelpGuestSession> findBySecretHash(String secretHash);

    long countByGuestId(Long guestId);

    @Modifying
    @Query("update HelpGuestSession s set s.lastSeenAt = :now where s.id = :id")
    int touch(Long id, Instant now);

    @Modifying
    @Query("delete from HelpGuestSession s where s.guestId = :guestId")
    int deleteAllOfGuest(Long guestId);

    @Modifying
    @Query("delete from HelpGuestSession s where s.lastSeenAt < :cutoff")
    int deleteIdleSince(Instant cutoff);
}
