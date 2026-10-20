package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.ReminderProperties;
import adliya.uz.functioncatalogservice.dto.QualityReminderResponse;
import adliya.uz.functioncatalogservice.entity.QualityIssueType;
import adliya.uz.functioncatalogservice.entity.QualityReminder;
import adliya.uz.functioncatalogservice.repository.OrgFunctionRepository;
import adliya.uz.functioncatalogservice.repository.QualityReminderRepository;
import adliya.uz.functioncatalogservice.security.CatalogAccess;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityReminderServiceTest {

    @Mock QualityReminderRepository reminders;
    @Mock OrgFunctionRepository functions;
    @Mock ActiveLanguageClient languages;
    @Mock CatalogAccess access;
    @Mock AuditWriter audit;
    @Spy ReminderProperties properties = new ReminderProperties();
    @InjectMocks QualityReminderService service;

    @Test void listIsCappedButTheTotalCountsEveryOpenReminder() {
        List<QualityReminder> open = LongStream.rangeClosed(1, QualityReminderService.LIST_LIMIT + 5)
                .mapToObj(id -> QualityReminder.builder().id(id).functionId(id).organizationId(10L)
                        .issue(QualityIssueType.SOURCE_MISSING).detectedAt(Instant.EPOCH).notifiedAt(Instant.EPOCH).build())
                .toList();
        when(access.scope(null)).thenReturn(OrganizationScope.of(Set.of(10L)));
        when(reminders.findAllByResolvedAtIsNullAndAcknowledgedAtIsNullAndOrganizationIdInOrderByNotifiedAtDescIdDesc(any()))
                .thenReturn(open);
        when(functions.mapById(any())).thenReturn(Map.of());

        QualityReminderResponse.Page page = service.active(null, null);

        assertThat(page.total()).isEqualTo(QualityReminderService.LIST_LIMIT + 5);
        assertThat(page.items()).hasSize(QualityReminderService.LIST_LIMIT);
        assertThat(page.items().get(0).id()).isEqualTo(1L);
    }

    @Test void staffWithoutOrganizationsSeeNothing() {
        when(access.scope(null)).thenReturn(OrganizationScope.of(Set.of()));
        assertThat(service.active(null, null)).isEqualTo(new QualityReminderResponse.Page(0, List.of()));
        verifyNoInteractions(reminders);
    }
}
