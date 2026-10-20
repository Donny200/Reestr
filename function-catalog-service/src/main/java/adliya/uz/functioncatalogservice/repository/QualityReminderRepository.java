package adliya.uz.functioncatalogservice.repository;

import adliya.uz.functioncatalogservice.entity.QualityReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QualityReminderRepository extends JpaRepository<QualityReminder, Long> {
    List<QualityReminder> findAllByResolvedAtIsNull();
    List<QualityReminder> findAllByResolvedAtIsNullAndAcknowledgedAtIsNullOrderByNotifiedAtDescIdDesc();
    List<QualityReminder> findAllByResolvedAtIsNullAndAcknowledgedAtIsNullAndOrganizationIdInOrderByNotifiedAtDescIdDesc(
            Collection<Long> organizationIds);
    void deleteAllByFunctionId(Long functionId);
}
