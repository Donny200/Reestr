package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.FunctionReviewMark;
import adliya.uz.functioncatalogservice.entity.ReviewSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FunctionReviewMarkRepository extends JpaRepository<FunctionReviewMark, Long> {
    List<FunctionReviewMark> findAllByFunctionIdAndReviewCycleOrderBySectionAsc(Long functionId, int reviewCycle);

    Optional<FunctionReviewMark> findByFunctionIdAndReviewCycleAndSection(Long functionId, int reviewCycle, ReviewSection section);

    @Modifying(flushAutomatically = true)
    @Query("update FunctionReviewMark m set m.functionId = :target where m.functionId = :source")
    int moveAll(@Param("source") Long source, @Param("target") Long target);
}
