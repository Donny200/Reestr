import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { FlagIcon } from 'lucide-react';
import { StatusPill } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { SkeletonText } from '../../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { exportReports } from '../../services/reportService';
import type { AnalyticsFilters, ReportCounts } from '../../types/analytics';
import type { InformationReport, ReportQuery } from '../../types/reports';
import { formatDateTime } from '../../utils/format';
import { useReports } from '../reports/queries';
import { canViewReports, reportCategoryLabel, reportStatusLabel, reportStatusTones } from '../reports/reportOptions';
import { ReportReviewDialog } from '../reports/ReportReviewDialog';
import { ExportButton } from './ExportButton';
import { MetricTile } from './MetricTile';

const PREVIEW = 5;

interface ReportsSectionProps {
  filters: AnalyticsFilters;
  counts: ReportCounts | undefined;
  loading: boolean;
}

export function ReportsSection({ filters, counts, loading }: ReportsSectionProps) {
  const { t, locale } = useI18n();
  const { hasPermission } = useAuth();
  const canView = canViewReports(hasPermission);
  const [selected, setSelected] = useState<InformationReport | null>(null);
  const number = useMemo(() => new Intl.NumberFormat(locale), [locale]);
  const openQuery: ReportQuery = { status: 'OPEN', organizationId: filters.organizationId, serviceCategoryId: filters.categoryId };
  const open = useReports(openQuery, canView);
  const periodQuery: ReportQuery = { ...openQuery, status: 'ALL', from: filters.from, to: filters.to };
  const reportsLink = `/admin/reports?${new URLSearchParams(
    Object.entries({
      organization: filters.organizationId ? String(filters.organizationId) : '',
      serviceCategory: filters.categoryId ? String(filters.categoryId) : '',
    }).filter(([, value]) => value),
  ).toString()}`;

  return (
    <Card>
      <CardHeader
        icon={<FlagIcon />}
        title={t('reports.dashboardSection', 'Visitor reports')}
        description={t('reports.dashboardSectionHint', 'Unresolved counts are current; received counts follow the selected period.')}
        actions={
          canView ? (
            <>
              <ExportButton label={t('reports.exportPeriod', 'Period CSV')} run={(delimiter) => exportReports(periodQuery, delimiter)} />
              <Link to={reportsLink} className={buttonVariants({ variant: 'outline', size: 'sm' })}>{t('action.viewAll')}</Link>
            </>
          ) : undefined
        }
      />
      <CardBody className="space-y-6">
        <div className="grid gap-3 min-[420px]:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-card-sm border border-line bg-background p-4">
            <p className="micro text-secondary">{t('reports.unresolved', 'Unresolved')}</p>
            <p className="mt-3 text-[1.75rem] font-semibold leading-none tabular-nums text-foreground">{loading ? '…' : number.format(counts?.unresolved ?? 0)}</p>
            <p className="mt-3 text-xs text-secondary">{t('reports.unresolvedHint', 'New or in progress right now')}</p>
          </div>
          <div className="rounded-card-sm border border-line bg-background p-4">
            <p className="micro text-secondary">{reportStatusLabel('NEW', t)}</p>
            <p className="mt-3 text-[1.75rem] font-semibold leading-none tabular-nums text-foreground">{loading ? '…' : number.format(counts?.newReports ?? 0)}</p>
            <p className="mt-3 text-xs text-secondary">{t('reports.newHint', 'Not picked up yet')}</p>
          </div>
          <div className="rounded-card-sm border border-line bg-background p-4">
            <p className="micro text-secondary">{reportStatusLabel('IN_PROGRESS', t)}</p>
            <p className="mt-3 text-[1.75rem] font-semibold leading-none tabular-nums text-foreground">{loading ? '…' : number.format(counts?.inProgress ?? 0)}</p>
            <p className="mt-3 text-xs text-secondary">{t('reports.inProgressHint', 'Being handled by staff')}</p>
          </div>
          <MetricTile
            label={t('reports.receivedInPeriod', 'Received in period')}
            value={counts?.receivedInPeriod ?? 0}
            previous={counts?.receivedInPreviousPeriod ?? 0}
            loading={loading}
          />
        </div>
        {canView && (
          <section aria-label={t('reports.openList', 'Open reports')}>
            <h3 className="text-base font-semibold text-foreground">{t('reports.openList', 'Open reports')}</h3>
            {open.isPending ? (
              <div className="mt-4"><SkeletonText lines={3} /></div>
            ) : open.error ? (
              <ErrorState error={open.error} onRetry={() => void open.refetch()} />
            ) : (open.data ?? []).length === 0 ? (
              <EmptyState title={t('reports.noOpenTitle', 'No open reports')} description={t('reports.noOpenText', 'Reports that need handling will appear here.')} />
            ) : (
              <ul className="mt-3 divide-y divide-line">
                {(open.data ?? []).slice(0, PREVIEW).map((report) => (
                  <li key={report.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <p className="font-medium text-foreground wrap-anywhere">{report.entityLabel}</p>
                      <p className="mt-0.5 flex flex-wrap items-center gap-2 text-sm text-secondary">
                        <StatusPill tone={reportStatusTones[report.status]}>{reportStatusLabel(report.status, t)}</StatusPill>
                        <span>{reportCategoryLabel(report.category, t)}</span>
                        <span className="tabular-nums">· {formatDateTime(report.createdAt, locale)}</span>
                      </p>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => setSelected(report)}>
                      {t('reports.review', 'Review')}
                      <span className="sr-only">: {report.entityLabel}</span>
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </CardBody>
      <ReportReviewDialog report={selected} onClose={() => setSelected(null)} />
    </Card>
  );
}
