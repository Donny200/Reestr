package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.InformationReport;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface InformationReportRepository extends JpaRepository<InformationReport, Long>, JpaSpecificationExecutor<InformationReport> {
    long countByEntityTypeAndEntityIdAndCreatedAtAfter(ReportEntityType entityType, Long entityId, Instant since);
    boolean existsByEntityTypeAndEntityIdAndDescriptionAndStatusInAndCreatedAtAfter(
            ReportEntityType entityType, Long entityId, String description, Collection<ReportStatus> statuses, Instant since);

    @Modifying
    @Query("update InformationReport r set r.organizationId = :organizationId where r.entityType = :type and r.entityId = :entityId")
    void reassignOrganization(@Param("type") ReportEntityType type, @Param("entityId") Long entityId,
                              @Param("organizationId") Long organizationId);
}
