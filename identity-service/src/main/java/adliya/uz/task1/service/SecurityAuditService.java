package adliya.uz.task1.service;

import adliya.uz.task1.entity.SecurityAuditEvent;
import adliya.uz.task1.repository.SecurityAuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writes security events for a user. Callers must never pass secrets, codes or tokens in {@code details}. */
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    public static final String MFA_ENROLLED = "MFA_ENROLLED";
    public static final String MFA_DISABLED = "MFA_DISABLED";
    public static final String MFA_RECOVERY_CODE_USED = "MFA_RECOVERY_CODE_USED";
    public static final String MFA_RECOVERY_CODES_REGENERATED = "MFA_RECOVERY_CODES_REGENERATED";
    public static final String MFA_ADMIN_RESET = "MFA_ADMIN_RESET";
    public static final String MFA_VERIFICATION_FAILED = "MFA_VERIFICATION_FAILED";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
    public static final String SESSIONS_REVOKED_BY_ADMIN = "SESSIONS_REVOKED_BY_ADMIN";
    public static final String SESSIONS_REVOKED = "SESSIONS_REVOKED";

    private final SecurityAuditEventRepository events;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long userId, Long actorUserId, String action, String details) {
        events.save(SecurityAuditEvent.builder().userId(userId).actorUserId(actorUserId).action(action)
                .details(details == null || details.length() <= 500 ? details : details.substring(0, 500)).build());
    }

    @Transactional(readOnly = true)
    public List<SecurityAuditEvent> recent(Long userId) {
        return events.findTop100ByUserIdOrderByPerformedAtDescIdDesc(userId);
    }
}
