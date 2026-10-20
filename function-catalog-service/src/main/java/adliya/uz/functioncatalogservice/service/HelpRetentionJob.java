package adliya.uz.functioncatalogservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily clean-up of idle anonymous support sessions and of optional contact details that are no longer needed. */
@Component
@RequiredArgsConstructor
@Slf4j
public class HelpRetentionJob {

    private final SupportIdentityService identities;

    @Scheduled(initialDelayString = "${catalog.help.retention-initial-delay:PT10M}",
            fixedDelayString = "${catalog.help.retention-interval:PT24H}")
    public void purge() {
        int removed = identities.purgeExpired();
        if (removed > 0) {
            log.info("Ended {} idle anonymous support browser sessions", removed);
        }
    }
}
