package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.FunctionPublicChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FunctionPublicChangeRepository extends JpaRepository<FunctionPublicChange, Long> {
    Optional<FunctionPublicChange> findFirstByFunctionIdOrderByPublishedAtDescIdDesc(Long functionId);
    List<FunctionPublicChange> findTop20ByFunctionIdOrderByPublishedAtDescIdDesc(Long functionId);
    boolean existsByFunctionId(Long functionId);

    /** Latest publication moment per card, for "last updated" on public lists. */
    @Query("select c.functionId, max(c.publishedAt) from FunctionPublicChange c where c.functionId in :ids group by c.functionId")
    List<Object[]> latestPublishedAt(@Param("ids") Collection<Long> ids);
}
