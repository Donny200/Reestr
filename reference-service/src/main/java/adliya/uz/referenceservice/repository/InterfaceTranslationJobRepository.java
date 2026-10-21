package adliya.uz.referenceservice.repository;

import adliya.uz.referenceservice.entity.InterfaceTranslationJob;
import adliya.uz.referenceservice.entity.InterfaceTranslationJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterfaceTranslationJobRepository extends JpaRepository<InterfaceTranslationJob, Long> {

    Optional<InterfaceTranslationJob> findByLanguageCode(String languageCode);

    List<InterfaceTranslationJob> findAllByLanguageCodeIn(Collection<String> languageCodes);

    /** PENDING jobs that are due, and RUNNING jobs whose lease expired (the worker stopped mid-way). */
    @Query("""
            select job from InterfaceTranslationJob job
            where job.status in (adliya.uz.referenceservice.entity.InterfaceTranslationJobStatus.PENDING,
                                 adliya.uz.referenceservice.entity.InterfaceTranslationJobStatus.RUNNING)
              and job.nextAttemptAt <= :now
            order by job.nextAttemptAt asc
            """)
    List<InterfaceTranslationJob> findDue(@Param("now") Instant now, Pageable page);

    List<InterfaceTranslationJob> findAllByStatus(InterfaceTranslationJobStatus status);
}
