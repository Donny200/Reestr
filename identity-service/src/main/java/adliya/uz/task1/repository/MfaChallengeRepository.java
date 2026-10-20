package adliya.uz.task1.repository;

import adliya.uz.task1.entity.MfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, Long> {
    Optional<MfaChallenge> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from MfaChallenge c where c.expiresAt < :cutoff or c.usedAt is not null")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
