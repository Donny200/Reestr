package adliya.uz.task1.repository;

import adliya.uz.task1.entity.RefreshToken;
import adliya.uz.task1.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user = :user and r.revoked = false")
    void revokeAllByUser(@Param("user") User user);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :now, r.revokedReason = :reason where r.user.id = :userId and r.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :now, r.revokedReason = :reason "
            + "where r.user.id = :userId and r.revoked = false and r.sessionId <> :sessionId")
    int revokeOtherSessions(@Param("userId") Long userId, @Param("sessionId") String sessionId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true, r.revokedAt = :now, r.revokedReason = :reason "
            + "where r.user.id = :userId and r.sessionId = :sessionId and r.revoked = false")
    int revokeSession(@Param("userId") Long userId, @Param("sessionId") String sessionId, @Param("now") Instant now, @Param("reason") String reason);

    List<RefreshToken> findAllByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(Long userId, Instant now);

    boolean existsBySessionIdAndRevokedFalseAndExpiresAtAfter(String sessionId, Instant now);
}