package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.AnalyticsProperties;
import adliya.uz.functioncatalogservice.dto.FeedbackReport;
import adliya.uz.functioncatalogservice.dto.FeedbackVoteRequest;
import adliya.uz.functioncatalogservice.dto.ReportingPeriod;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.repository.FeedbackDailyCountRepository;
import adliya.uz.functioncatalogservice.repository.FeedbackStatistics.ServiceTotals;
import adliya.uz.functioncatalogservice.repository.FeedbackStatistics.Totals;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.StatisticsFilter;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Anonymous, aggregate "Was this information helpful?" votes. Only daily counts per card are stored: no visitor
 * identifiers, addresses, accounts or device fingerprints. Browser-side de-duplication is best effort; the counts
 * describe interest in the catalog text, not the quality of the organization, its service or its staff.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Comparator<FeedbackReport.ServiceFeedback> MOST_VOTED = Comparator
            .comparingLong(FeedbackReport.ServiceFeedback::total).reversed()
            .thenComparing(FeedbackReport.ServiceFeedback::functionId);

    private final OrgFunctionRepository functions;
    private final FeedbackDailyCountRepository counts;
    private final FeedbackRateLimiter rateLimiter;
    private final CatalogAccess access;
    private final AnalyticsProperties properties;
    private final EngagementInsightsService engagement;

    /** Counts one vote; signed-in staff are ignored and unpublished cards are 404 like every public read. */
    @Transactional
    public boolean record(FeedbackVoteRequest request, String clientAddress) {
        if (access.authenticated()) {
            return false;
        }
        rateLimiter.acquire(clientAddress);
        OrgFunction function = functions.requirePublished(request.serviceId());
        boolean helpful = Boolean.TRUE.equals(request.helpful());
        counts.increment(LocalDate.now(properties.getZone()), function.getId(), function.getOrganizationId(), helpful ? 1 : 0, helpful ? 0 : 1);
        return true;
    }

    @Transactional(readOnly = true)
    public FeedbackReport report(Long organizationId, Long categoryId, LocalDate from, LocalDate to) {
        OrganizationScope scope = access.scope(organizationId);
        ReportingPeriod period = engagement.period(from, to);
        if (scope.empty()) {
            return new FeedbackReport(engagement.periodInfo(period), FeedbackReport.Totals.of(0, 0), FeedbackReport.Totals.of(0, 0), List.of());
        }
        StatisticsFilter filter = new StatisticsFilter(scope, categoryId);
        Totals current = counts.totals(filter, period.from(), period.to());
        Totals previous = counts.totals(filter, period.previous().from(), period.previous().to());
        List<ServiceTotals> rows = counts.byService(filter, period.from(), period.to());
        Set<Long> ids = rows.stream().map(ServiceTotals::functionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, OrgFunction> cards = functions.mapById(ids);
        List<FeedbackReport.ServiceFeedback> services = rows.stream().map(row -> {
            OrgFunction card = cards.get(row.functionId());
            return new FeedbackReport.ServiceFeedback(row.functionId(), card == null ? null : card.getName(),
                    card == null ? null : card.getNameTranslations(), card == null ? null : card.getStatus(), row.organizationId(),
                    card == null || card.getFunctionCategory() == null ? null : card.getFunctionCategory().getId(),
                    card == null ? null : card.getCategory(), row.helpful(), row.notHelpful(), row.helpful() + row.notHelpful());
        }).sorted(MOST_VOTED).toList();
        return new FeedbackReport(engagement.periodInfo(period), FeedbackReport.Totals.of(current.helpful(), current.notHelpful()),
                FeedbackReport.Totals.of(previous.helpful(), previous.notHelpful()), services);
    }

    @Transactional
    public int purgeExpired() {
        LocalDate cutoff = LocalDate.now(properties.getZone()).minusDays(properties.getRetention().toDays());
        return counts.deleteOlderThan(cutoff);
    }
}
