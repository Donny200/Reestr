package adliya.uz.task1.repository;

import adliya.uz.task1.entity.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserMfaRepository extends JpaRepository<UserMfa, Long> {
    @Query("select m.userId from UserMfa m where m.confirmedAt is not null")
    List<Long> findConfirmedUserIds();
}
