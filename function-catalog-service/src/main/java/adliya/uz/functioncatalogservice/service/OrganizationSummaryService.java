package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.dto.OrganizationSummary;
import adliya.uz.functioncatalogservice.dto.ReportingPeriod;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import adliya.uz.functioncatalogservice.dto.ReportQuery;
import adliya.uz.functioncatalogservice.entity.OrgFunction;
import adliya.uz.functioncatalogservice.entity.ReportEntityType;
import adliya.uz.functioncatalogservice.entity.ReportStatus;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationSummaryService {

    private final CatalogAccess access;
    private final ServiceQualityService quality;
    private final ReportReviewService reports;
    private final QualityReminderService reminders;
    private final EngagementInsightsService engagement;
    private final OrgFunctionRepository functions;

    public OrganizationSummary summary(Long organizationId, Long categoryId, LocalDate from, LocalDate to) {
        OrganizationScope scope = access.scope(organizationId);
        ReportingPeriod period = engagement.period(from, to);
        ServiceQualityService.Snapshot snapshot = quality.snapshot(scope, categoryId);
        return new OrganizationSummary(Instant.now(), engagement.periodInfo(period), organizationId, categoryId,
                snapshot.statuses(), snapshot.servicesNeedingAttention(), snapshot.attention(),
                snapshot.translationsChecked(), snapshot.activeLanguages(),
                reports.counts(scope, categoryId, period),
                reminders.activeCount(scope, snapshot.functionIds()),
                assignedToMe(scope, snapshot.functionIds()));
    }

    private OrganizationSummary.AssignedToMe assignedToMe(OrganizationScope scope, Set<Long> functionIds) {
        Long me = access.principal().userId();
        if (me == null || scope.empty()) {
            return new OrganizationSummary.AssignedToMe(0, 0, 0);
        }
        Set<Long> mine = functions.findAllByAssignedUserId(me).stream()
                .filter(card -> !card.isRevision() && functionIds.contains(card.getId()))
                .map(OrgFunction::getId)
                .collect(Collectors.toSet());
        if (mine.isEmpty()) {
            return new OrganizationSummary.AssignedToMe(0, 0, 0);
        }
        long openReports = reports.count(new ReportQuery(ReportStatus.OPEN, null, ReportEntityType.FUNCTION, null, null, null, null,
                null, me));
        return new OrganizationSummary.AssignedToMe(mine.size(), reminders.activeCount(scope, mine, me), openReports);
    }
}
