package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface OrgFunctionRepository extends JpaRepository<OrgFunction, Long> {
    Page<OrgFunction> findAllByOrganizationIdIn(Collection<Long> organizationIds, Pageable pageable);
    List<OrgFunction> findAllByStatus(FunctionStatus status);
    Optional<OrgFunction> findByIdAndStatus(Long id, FunctionStatus status);
    List<OrgFunction> findAllByOrganizationIdAndStatus(Long organizationId, FunctionStatus status);
    List<OrgFunction> findAllByFunctionCategory_NameAndStatus(String category, FunctionStatus status);
    List<OrgFunction> findAllByOrganizationIdIn(Collection<Long> organizationIds);
    List<OrgFunction> findAllByStatusAndOrganizationIdIn(FunctionStatus status, Collection<Long> organizationIds);
    boolean existsByFunctionCategory_Id(Long categoryId);
    boolean existsByOrganizationIdAndStatus(Long organizationId, FunctionStatus status);
    Optional<OrgFunction> findBySeedKey(String seedKey);
    Optional<OrgFunction> findByRevisionOfId(Long revisionOfId);
    List<OrgFunction> findAllByRevisionOfIdIn(Collection<Long> revisionOfIds);
    List<OrgFunction> findAllByAssignedUserId(Long assignedUserId);

    /** A published card as the public site sees it; anything else is "not found". */
    default OrgFunction requirePublished(Long id) {
        return findByIdAndStatus(id, FunctionStatus.PUBLISHED).orElseThrow(() -> new NoSuchElementException("Service not found: " + id));
    }

    default Map<Long, OrgFunction> mapById(Collection<Long> ids) {
        return findAllById(ids).stream().collect(Collectors.toMap(OrgFunction::getId, Function.identity()));
    }
}
