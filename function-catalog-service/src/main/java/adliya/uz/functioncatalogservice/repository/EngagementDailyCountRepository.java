package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.EngagementDailyCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface EngagementDailyCountRepository extends JpaRepository<EngagementDailyCount, Long>, EngagementStatistics {

    @Modifying
    @Query(value = """
            INSERT INTO engagement_daily_counts (activity_date, event_type, organization_id, function_id, event_count)
            VALUES (:day, :type, :organizationId, :functionId, 1)
            ON CONFLICT ON CONSTRAINT engagement_daily_counts_key
            DO UPDATE SET event_count = engagement_daily_counts.event_count + 1
            """, nativeQuery = true)
    void increment(@Param("day") LocalDate day, @Param("type") String type,
                   @Param("organizationId") Long organizationId, @Param("functionId") Long functionId);

    @Modifying
    @Query("delete from EngagementDailyCount e where e.activityDate < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
