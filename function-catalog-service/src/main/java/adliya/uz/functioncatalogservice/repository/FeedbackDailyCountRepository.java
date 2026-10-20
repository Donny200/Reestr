package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.FeedbackDailyCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface FeedbackDailyCountRepository extends JpaRepository<FeedbackDailyCount, Long>, FeedbackStatistics {

    @Modifying
    @Query(value = """
            INSERT INTO feedback_daily_counts (activity_date, function_id, organization_id, helpful_count, not_helpful_count)
            VALUES (:day, :functionId, :organizationId, :helpful, :notHelpful)
            ON CONFLICT ON CONSTRAINT feedback_daily_counts_key
            DO UPDATE SET helpful_count = feedback_daily_counts.helpful_count + EXCLUDED.helpful_count,
                          not_helpful_count = feedback_daily_counts.not_helpful_count + EXCLUDED.not_helpful_count
            """, nativeQuery = true)
    void increment(@Param("day") LocalDate day, @Param("functionId") Long functionId, @Param("organizationId") Long organizationId,
                   @Param("helpful") int helpful, @Param("notHelpful") int notHelpful);

    @Modifying
    @Query("delete from FeedbackDailyCount d where d.activityDate < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
