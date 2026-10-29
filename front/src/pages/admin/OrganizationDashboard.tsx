import { useRef, useState } from 'react';
import { AlertTriangleIcon, ArchiveIcon, BellIcon, FileCheck2Icon, FilePenLineIcon, FileSearchIcon, FileWarningIcon, FlagIcon, InfoIcon, LanguagesIcon, LinkIcon, ShieldAlertIcon, UserCheckIcon } from 'lucide-react';
import type { ComponentType } from 'react';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card, CardBody } from '../../components/ui/Card';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Skeleton } from '../../components/ui/Skeleton';
import { StatCard } from '../../components/ui/StatCard';
import { ErrorState } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { EngagementSection } from '../../features/analytics/EngagementSection';
import { ExportButton } from '../../features/analytics/ExportButton';
import {
  DEFAULT_PRESET,
  MAX_PERIOD_DAYS,
  PERIOD_PRESETS,
  formatPeriod,
  isIsoDate,
  parsePreset,
  presetRange,
  rangeError,
  reportingToday,
  type PeriodPreset,
} from '../../features/analytics/period';
import { QualityQueueSection } from '../../features/analytics/QualityQueueSection';
import { qualityIssueHints, issueLabel } from '../../features/analytics/qualityIssues';
import { useEngagementReport, useOrganizationSummary } from '../../features/analytics/queries';
import { RemindersSection } from '../../features/analytics/RemindersSection';
import { ReportsSection } from '../../features/analytics/ReportsSection';
import { AssignmentsSection } from '../../features/analytics/AssignmentsSection';
import { FeedbackSection } from '../../features/analytics/FeedbackSection';
import { parseId, useScopeOptions } from '../../features/analytics/scope';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { exportOrganizationReport } from '../../services/analyticsService';
import { useHashTarget } from '../../hooks/useHashTarget';
import { functionStatusLabel, type FunctionStatus } from '../../types/adminFunctions';
import { QUALITY_ISSUE_TYPES, type AnalyticsFilters, type QualityIssueType } from '../../types/analytics';
import { usePatchSearchParams } from '../../hooks/usePatchSearchParams';

const statusIcons: Record<FunctionStatus, ComponentType<{ className?: string }>> = {
  PUBLISHED: FileCheck2Icon,
  PENDING_REVIEW: FileSearchIcon,
  CHANGES_REQUESTED: FileWarningIcon,
  DRAFT: FilePenLineIcon,
  DEACTIVATED: ArchiveIcon,
};

const SNAPSHOT_ORDER: FunctionStatus[] = ['PUBLISHED', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'DRAFT', 'DEACTIVATED'];

const issueIcons: Record<QualityIssueType, ComponentType<{ className?: string }>> = {
  VERIFICATION_OVERDUE: ShieldAlertIcon,
  SOURCE_MISSING: LinkIcon,
  INFORMATION_INCOMPLETE: AlertTriangleIcon,
  TRANSLATIONS_MISSING: LanguagesIcon,
};

export function OrganizationDashboard() {
  const { t, locale } = useI18n();
  const { hasPermission } = useAuth();
  const scope = useScopeOptions();
  const [params, update] = usePatchSearchParams();
  const [issue, setIssue] = useState<QualityIssueType | ''>('');
  const [status, setStatus] = useState<FunctionStatus | ''>('');
  const queueRef = useRef<HTMLDivElement>(null);
  useHashTarget();

  const preset = parsePreset(params.get('period'));
  const customFrom = params.get('from');
  const customTo = params.get('to');
  const range =
    preset === 'custom' && isIsoDate(customFrom) && isIsoDate(customTo)
      ? { from: customFrom, to: customTo }
      : presetRange(preset === 'custom' ? DEFAULT_PRESET : preset);
  const problem = rangeError(range.from, range.to);
  const filters: AnalyticsFilters = {
    organizationId: parseId(params.get('organization')),
    categoryId: parseId(params.get('category')),
    from: range.from,
    to: range.to,
  };
  const summary = useOrganizationSummary(filters, problem === null);
  const engagement = useEngagementReport(filters, problem === null);

  const choosePreset = (value: string) => {
    const next = value as PeriodPreset;
    if (next === 'custom') update({ period: 'custom', from: range.from, to: range.to });
    else update({ period: next === DEFAULT_PRESET ? '' : next, from: '', to: '' });
  };

  const focusQueue = (type: QualityIssueType) => {
    setIssue(type);
    setStatus('');
    window.requestAnimationFrame(() => {
      queueRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      queueRef.current?.focus({ preventScroll: true });
    });
  };

  const presetLabel = (value: string) =>
    value === 'custom' ? t('analytics.customPeriod', 'Custom period') : t(`analytics.preset.${value}`, `Last ${value} days`);

  const data = summary.data;
  const canViewReportsHere = hasPermission('REPORTS_VIEW') || hasPermission('ORG_REPORTS_MANAGE');
  const functionsLink = (value: FunctionStatus) => (hasPermission('FUNCTIONS_VIEW') ? `/admin/functions?status=${value}` : undefined);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('analytics.title', 'Organization dashboard')}
        description={t('analytics.subtitle', 'Service card status, quality issues, visitor reports and interest in your catalog information.')}
        actions={
          <div className="flex flex-wrap gap-2" role="group" aria-label={t('export.reportTitle', 'Download the report for these filters')}>
            <ExportButton label={t('export.xlsx', 'Export Excel')} disabled={problem !== null} run={() => exportOrganizationReport(filters, 'XLSX', locale)} />
            <ExportButton label={t('export.pdf', 'Export PDF')} disabled={problem !== null} run={() => exportOrganizationReport(filters, 'PDF', locale)} />
          </div>
        }
      />

      <Card>
        <CardBody className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {scope.showOrganizations && (
            <Field label={t('field.organization')}>
              {(control) => (
                <Select
                  {...control}
                  size="sm"
                  value={filters.organizationId ? String(filters.organizationId) : ''}
                  onValueChange={(value) => update({ organization: value })}
                  options={scope.organizations}
                />
              )}
            </Field>
          )}
          <Field label={t('analytics.category', 'Service category')}>
            {(control) => (
              <Select
                {...control}
                size="sm"
                value={filters.categoryId ? String(filters.categoryId) : ''}
                onValueChange={(value) => update({ category: value })}
                options={scope.categories}
              />
            )}
          </Field>
          <Field label={t('analytics.period', 'Period')}>
            {(control) => (
              <Select
                {...control}
                size="sm"
                value={preset}
                onValueChange={choosePreset}
                options={[...PERIOD_PRESETS, 'custom'].map((value) => ({ value, label: presetLabel(value) }))}
              />
            )}
          </Field>
          {preset === 'custom' && (
            <div className="grid grid-cols-2 gap-3">
              <Field label={t('analytics.from', 'From')}>
                {(control) => (
                  <Input
                    {...control}
                    type="date"
                    className="min-h-10 py-2 text-sm"
                    max={reportingToday()}
                    value={range.from}
                    onChange={(event) => {
                      if (isIsoDate(event.target.value)) update({ from: event.target.value });
                    }}
                  />
                )}
              </Field>
              <Field label={t('analytics.to', 'To')}>
                {(control) => (
                  <Input
                    {...control}
                    type="date"
                    className="min-h-10 py-2 text-sm"
                    max={reportingToday()}
                    value={range.to}
                    onChange={(event) => {
                      if (isIsoDate(event.target.value)) update({ to: event.target.value });
                    }}
                  />
                )}
              </Field>
            </div>
          )}
          <p className="flex items-start gap-2 text-sm leading-6 text-secondary md:col-span-2 xl:col-span-4">
            <InfoIcon className="mt-1 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>
              {t('analytics.filterScope', 'Status counts, quality issues and unresolved reports show the current state. The period applies to catalog engagement and to reports received.')}{' '}
              <span className="tabular-nums">{formatPeriod(range.from, range.to, locale)} ({t('analytics.timeZone', 'Tashkent time')})</span>
            </span>
          </p>
        </CardBody>
      </Card>

      {problem && (
        <InlineAlert tone="danger">
          {problem === 'order'
            ? t('analytics.rangeOrder', 'The start date must not be after the end date')
            : t('analytics.rangeTooLong', `Choose a period of at most ${MAX_PERIOD_DAYS} days`)}
        </InlineAlert>
      )}

      <InlineAlert tone="info">
        {t('analytics.disclaimer', 'Engagement figures count anonymous views and link clicks on this information site. They are not applications, customers or completed services, and staff visits are not counted.')}
      </InlineAlert>

      {summary.error ? (
        <Card><ErrorState error={summary.error} onRetry={() => void summary.refetch()} /></Card>
      ) : (
        <>
          <section aria-labelledby="snapshot-title">
            <h2 id="snapshot-title" className="mb-3 text-lg font-semibold text-foreground">{t('analytics.snapshotTitle', 'Service cards now')}</h2>
            <div className="grid gap-4 min-[420px]:grid-cols-2 xl:grid-cols-4">
              {SNAPSHOT_ORDER.map((value) => (
                <StatCard
                  key={value}
                  label={functionStatusLabel(value, t)}
                  value={data?.services[value] ?? 0}
                  hint={t('analytics.currentState', 'Current state')}
                  icon={statusIcons[value]}
                  to={functionsLink(value)}
                  loading={summary.isPending}
                />
              ))}
            </div>
          </section>

          <section aria-labelledby="attention-title">
            <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
              <h2 id="attention-title" className="text-lg font-semibold text-foreground">{t('analytics.attentionTitle', 'Needs attention')}</h2>
              {data && (
                <p className="text-sm text-secondary">
                  {t('analytics.cardsWithIssues', 'Cards with at least one issue:')} <span className="font-semibold tabular-nums text-foreground">{data.servicesNeedingAttention}</span>
                </p>
              )}
            </div>
            <div className="grid gap-4 min-[420px]:grid-cols-2 xl:grid-cols-4">
              {QUALITY_ISSUE_TYPES.map((type) => {
                const Icon = issueIcons[type];
                const unchecked = type === 'TRANSLATIONS_MISSING' && data && !data.translationsChecked;
                return (
                  <button
                    key={type}
                    type="button"
                    onClick={() => focusQueue(type)}
                    className="group rounded-card-sm border border-line bg-background p-5 text-start transition-transform duration-snap ease-snap motion-safe:fine:hover:-translate-y-1"
                  >
                    <span className="flex items-start justify-between gap-3">
                      <span className="micro text-secondary">{issueLabel(type, t)}</span>
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-pill bg-surface text-foreground">
                        <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
                      </span>
                    </span>
                    {summary.isPending ? (
                      <Skeleton className="mt-4 h-9 w-16" />
                    ) : (
                      <span className="mt-4 block text-[2.25rem] font-semibold leading-none tabular-nums text-foreground">
                        {unchecked ? '—' : (data?.attention[type] ?? 0)}
                      </span>
                    )}
                    <span className="mt-3 block text-sm text-secondary">
                      {unchecked
                        ? t('quality.languagesUnknown', 'Language list unavailable')
                        : t(`quality.hint.${type}`, qualityIssueHints[type])}
                    </span>
                    <span className="sr-only">{t('analytics.showInQueue', 'Show these cards in the queue')}</span>
                  </button>
                );
              })}
            </div>
          </section>
        </>
      )}

      {data?.assignedToMe && (
        <section aria-labelledby="assigned-to-me-title">
          <h2 id="assigned-to-me-title" className="mb-3 text-lg font-semibold text-foreground">{t('assignments.mineTitle', 'Assigned to you')}</h2>
          <div className="grid gap-4 min-[420px]:grid-cols-2 xl:grid-cols-4">
            <StatCard label={t('assignments.myServices', 'Your service cards')} value={data.assignedToMe.services} hint={t('assignments.myServicesHint', 'You keep these current')} icon={UserCheckIcon} loading={summary.isPending} to={hasPermission('FUNCTIONS_VIEW') ? '/admin/functions?assignee=me' : undefined} />
            <StatCard label={t('assignments.myReminders', 'Your open reminders')} value={data.assignedToMe.openReminders} hint={t('reminders.dashboardHint', 'Quality issues to follow up')} icon={BellIcon} loading={summary.isPending} to="#reminders" />
            <StatCard label={t('assignments.myReports', 'Reports on your cards')} value={data.assignedToMe.openReports} hint={t('reports.unresolvedHint', 'New or in progress right now')} icon={FlagIcon} loading={summary.isPending} to={canViewReportsHere ? '/admin/reports?assignee=me' : undefined} />
          </div>
        </section>
      )}

      <RemindersSection organizationId={filters.organizationId} categoryId={filters.categoryId} />

      <AssignmentsSection organizationId={filters.organizationId} organizationName={scope.organizationName} showOrganization={scope.showOrganizations} />

      <ReportsSection filters={filters} counts={data?.reports} loading={summary.isPending} />

      <EngagementSection filters={filters} report={engagement} />

      <FeedbackSection filters={filters} organizationName={scope.organizationName} showOrganization={scope.showOrganizations} />

      <QualityQueueSection
        ref={queueRef}
        organizationId={filters.organizationId}
        categoryId={filters.categoryId}
        issue={issue}
        status={status}
        onIssueChange={setIssue}
        onStatusChange={setStatus}
        organizationName={scope.organizationName}
        showOrganization={scope.showOrganizations}
      />
    </div>
  );
}
