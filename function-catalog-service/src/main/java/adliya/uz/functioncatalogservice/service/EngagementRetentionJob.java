package adliya.uz.functioncatalogservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EngagementRetentionJob {

    private final EngagementInsightsService engagement;
    private final FeedbackService feedback;

    @Scheduled(initialDelayString = "${catalog.analytics.retention-initial-delay:PT5M}",
            fixedDelayString = "${catalog.analytics.retention-interval:PT24H}")
    public void purge() {
        int removed = engagement.purgeExpired();
        if (removed > 0) {
            log.info("Removed {} expired engagement aggregate rows", removed);
        }
        int feedbackRemoved = feedback.purgeExpired();
        if (feedbackRemoved > 0) {
            log.info("Removed {} expired feedback aggregate rows", feedbackRemoved);
        }
    }
}
