package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.TranslationJob;
import adliya.uz.functioncatalogservice.entity.TranslationJobStatus;
import adliya.uz.functioncatalogservice.entity.TranslationTarget;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TranslationJobRepository extends JpaRepository<TranslationJob, Long> {
    Optional<TranslationJob> findByTargetTypeAndTargetId(TranslationTarget targetType, Long targetId);
    List<TranslationJob> findAllByTargetTypeAndTargetIdIn(TranslationTarget targetType, Collection<Long> targetIds);
    long countByStatus(TranslationJobStatus status);
    void deleteByTargetTypeAndTargetId(TranslationTarget targetType, Long targetId);

    /** Targets with a job that is waiting or running ({@link TranslationJob#isOpen()}). */
    @Query("select j.targetId from TranslationJob j where j.targetType = :targetType and j.status in ('PENDING', 'RUNNING')")
    Set<Long> findOpenTargetIds(@Param("targetType") TranslationTarget targetType);

    /** Pending jobs whose time has come, plus running jobs whose lease expired (worker died mid-run). */
    @Query("select j from TranslationJob j where j.nextAttemptAt <= :now and j.status in ('PENDING', 'RUNNING') order by j.nextAttemptAt, j.id")
    List<TranslationJob> findDue(Instant now, Pageable pageable);
}
