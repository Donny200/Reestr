package adliya.uz.task1.repository;

import adliya.uz.task1.entity.PasswordResetToken;
import adliya.uz.task1.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** A newer reset supersedes every outstanding token of the same user. */
    @Modifying
    @Query("update PasswordResetToken t set t.invalidatedAt = :now where t.user = :user and t.usedAt is null and t.invalidatedAt is null")
    int invalidateOutstanding(@Param("user") User user, @Param("now") Instant now);
}
