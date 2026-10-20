package adliya.uz.task1.repository;

import adliya.uz.task1.entity.SecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
    List<SecurityAuditEvent> findTop100ByUserIdOrderByPerformedAtDescIdDesc(Long userId);
}
