package adliya.uz.functioncatalogservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class QualityReminderJob {

    private final QualityReminderService reminders;

    @Scheduled(initialDelayString = "${catalog.reminders.scan-initial-delay:PT2M}",
            fixedDelayString = "${catalog.reminders.scan-interval:PT24H}")
    public void scan() {
        try {
            QualityReminderService.ScanResult result = reminders.scan(Instant.now());
            if (result.created() + result.renotified() + result.resolved() > 0) {
                log.info("Quality reminders: {} new, {} repeated, {} resolved",
                        result.created(), result.renotified(), result.resolved());
            }
        } catch (DataAccessException exception) {
            log.warn("Quality reminder scan skipped: {}", exception.getMessage());
        }
    }
}
