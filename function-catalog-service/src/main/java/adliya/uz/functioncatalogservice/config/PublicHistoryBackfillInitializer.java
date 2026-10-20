package adliya.uz.functioncatalogservice.config;

import adliya.uz.functioncatalogservice.service.PublicChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Idempotent: gives cards published before the public history existed their first entry. */
@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "catalog.public-history.backfill-enabled", havingValue = "true", matchIfMissing = true)
public class PublicHistoryBackfillInitializer implements CommandLineRunner {

    private final PublicChangeService publicChanges;

    @Override
    public void run(String... args) {
        try {
            int created = publicChanges.backfill(Instant.now());
            if (created > 0) {
                log.info("Public change history: created first entries for {} previously published cards", created);
            }
        } catch (DataAccessException exception) {
            log.warn("Public change history backfill skipped: {}", exception.getMessage());
        }
    }
}
