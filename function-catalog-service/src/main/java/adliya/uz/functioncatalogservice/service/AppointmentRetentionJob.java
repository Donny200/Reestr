package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AppointmentProperties;
import adliya.uz.functioncatalogservice.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Daily removal of visitor names and contact details of appointments that ended longer ago than the retention period. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentRetentionJob {

    private final AppointmentRepository appointments;
    private final AppointmentProperties properties;

    @Scheduled(initialDelayString = "${catalog.appointments.retention-initial-delay:PT15M}",
            fixedDelayString = "${catalog.appointments.retention-interval:PT24H}")
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        int removed = appointments.removeContacts(now.minus(properties.getContactRetention()), now);
        if (removed > 0) {
            log.info("Removed visitor details of {} past appointments", removed);
        }
    }
}
