package adliya.uz.referenceservice.translation;

import adliya.uz.referenceservice.config.InterfaceTranslationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Periodic scan and processing; does nothing while the provider is unconfigured or the worker is switched off. */
@Component
@RequiredArgsConstructor
@Slf4j
public class InterfaceMachineTranslationScheduler {

    private final InterfaceMachineTranslationService service;
    private final InterfaceTranslationProperties properties;
    private volatile Instant lastScan = Instant.EPOCH;

    @Scheduled(initialDelayString = "${interface.translation.initial-delay:PT1M}",
            fixedDelayString = "${interface.translation.interval:PT2M}")
    public void tick() {
        if (!service.automatic()) {
            return;
        }
        Instant now = Instant.now();
        try {
            if (!lastScan.plus(properties.getScanInterval()).isAfter(now)) {
                lastScan = now;
                List<String> queued = service.scan(now);
                if (!queued.isEmpty()) {
                    log.info("Interface machine translation queued for: {}", String.join(", ", queued));
                }
            }
            InterfaceMachineTranslationService.RunResult result = service.runDue(now);
            if (result.claimed() > 0) {
                log.info("Interface machine translation: {} language job(s), {} done, {} queued again, {} unsupported, {} failed",
                        result.claimed(), result.done(), result.retried(), result.unsupported(), result.failed());
            }
        } catch (DataAccessException exception) {
            log.warn("Interface machine translation tick skipped: {}", exception.getMessage());
        }
    }
}
