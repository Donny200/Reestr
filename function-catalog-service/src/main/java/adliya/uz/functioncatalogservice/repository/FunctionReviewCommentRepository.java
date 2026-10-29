package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.FunctionReviewComment;
import adliya.uz.functioncatalogservice.entity.ReviewCommentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface FunctionReviewCommentRepository extends JpaRepository<FunctionReviewComment, Long> {
    List<FunctionReviewComment> findAllByFunctionIdInOrderByCreatedAtAscIdAsc(Collection<Long> functionIds);

    List<FunctionReviewComment> findAllByFunctionIdAndStatusOrderByIdAsc(Long functionId, ReviewCommentStatus status);

    boolean existsByFunctionIdAndFieldPathAndStatus(Long functionId, String fieldPath, ReviewCommentStatus status);

    @Query("select c.functionId, count(c) from FunctionReviewComment c where c.functionId in :ids and c.status = 'OPEN' group by c.functionId")
    List<Object[]> countOpenByFunctionIds(@Param("ids") Collection<Long> ids);

    default Map<Long, Long> openCounts(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return countOpenByFunctionIds(ids).stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** Re-attaches the notes of a draft revision to the published card it belonged to (history is never deleted). */
    @Modifying(flushAutomatically = true)
    @Query("update FunctionReviewComment c set c.functionId = :target where c.functionId = :source")
    int moveAll(@Param("source") Long source, @Param("target") Long target);
}
