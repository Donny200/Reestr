import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ExternalLinkIcon, PencilIcon } from 'lucide-react';
import { FunctionStatusBadge } from '../../components/ui/Badge';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { SkeletonText } from '../../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { Tabs } from '../../components/ui/Tabs';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { exportEngagement } from '../../services/analyticsService';
import type { AnalyticsFilters, EngagementCounts, EngagementEventType, EngagementReport } from '../../types/analytics';
import { localizedText } from '../../utils/translations';
import { ExportButton } from './ExportButton';
import { engagementLabels } from './labels';
import { MetricTile } from './MetricTile';
import { formatPeriod } from './period';
import { TrendChart } from './TrendChart';

type Metric = 'SERVICE_VIEW' | 'CATALOG_VIEW' | 'LINK_CLICKS' | 'PRINT';

const METRICS: Metric[] = ['SERVICE_VIEW', 'CATALOG_VIEW', 'LINK_CLICKS', 'PRINT'];
const LINK_EVENTS: EngagementEventType[] = ['OFFICIAL_LINK_CLICK', 'PHONE_CLICK', 'MAP_CLICK'];

function metricValue(counts: EngagementCounts, metric: Metric): number {
  return metric === 'LINK_CLICKS' ? LINK_EVENTS.reduce((sum, type) => sum + counts[type], 0) : counts[metric];
}

interface EngagementSectionProps {
  filters: AnalyticsFilters;
  report: { data?: EngagementReport; isPending: boolean; error: unknown; refetch: () => unknown };
}

export function EngagementSection({ filters, report }: EngagementSectionProps) {
  const { t, locale } = useI18n();
  const { hasPermission } = useAuth();
  const [metric, setMetric] = useState<Metric>('SERVICE_VIEW');
  const number = useMemo(() => new Intl.NumberFormat(locale), [locale]);
  const data = report.data;
  const label = (type: EngagementEventType) => t(`analytics.event.${type}`, engagementLabels[type]);
  const metricLabel = (value: Metric) => (value === 'LINK_CLICKS' ? t('analytics.linkClicks', 'Link clicks') : label(value));
  const top = data?.topServices ?? [];
  const topViews = Math.max(1, ...top.map((item) => item.views));

  return (
    <Card>
      <CardHeader
        title={t('analytics.engagementTitle', 'Catalog engagement')}
        description={
          data
            ? `${formatPeriod(data.period.from, data.period.to, locale)} · ${t('analytics.previousPeriod', 'Previous period:')} ${formatPeriod(data.period.previousFrom, data.period.previousTo, locale)}`
            : undefined
        }
        actions={
          <>
            <ExportButton label={t('analytics.exportServices', 'Services CSV')} run={(delimiter) => exportEngagement(filters, 'SERVICES', delimiter)} />
            <ExportButton label={t('analytics.exportDaily', 'Daily CSV')} run={(delimiter) => exportEngagement(filters, 'DAILY', delimiter)} />
          </>
        }
      />
      {report.error ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <CardBody className="space-y-6">
          <div className="grid gap-3 min-[420px]:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
            {(Object.keys(engagementLabels) as EngagementEventType[]).map((type) => (
              <MetricTile
                key={type}
                label={label(type)}
                value={data?.totals[type] ?? 0}
                previous={data?.previousTotals[type] ?? 0}
                loading={report.isPending}
              />
            ))}
          </div>

          <section aria-labelledby="engagement-trend-title">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <h3 id="engagement-trend-title" className="text-base font-semibold text-foreground">
                {data?.granularity === 'WEEK' ? t('analytics.weeklyTrend', 'Weekly trend') : t('analytics.dailyTrend', 'Daily trend')}
              </h3>
              <Tabs
                label={t('analytics.metric', 'Metric')}
                value={metric}
                onChange={(value) => setMetric(value as Metric)}
                items={METRICS.map((value) => ({ id: value, label: metricLabel(value) }))}
              />
            </div>
            <div className="mt-4">
              {report.isPending || !data ? (
                <SkeletonText lines={6} />
              ) : (
                <TrendChart
                  granularity={data.granularity}
                  metricLabel={metricLabel(metric)}
                  points={data.series.map((point) => ({ start: point.start, end: point.end, value: metricValue(point.counts, metric) }))}
                />
              )}
            </div>
          </section>

          <section aria-labelledby="engagement-top-title">
            <h3 id="engagement-top-title" className="text-base font-semibold text-foreground">{t('analytics.mostViewed', 'Most viewed services')}</h3>
            {report.isPending ? (
              <div className="mt-4"><SkeletonText lines={4} /></div>
            ) : top.length === 0 ? (
              <EmptyState
                title={t('analytics.noViewsTitle', 'No service page views in this period')}
                description={t('analytics.noViewsText', 'Views appear here after visitors open published service pages.')}
              />
            ) : (
              <ol className="mt-4 space-y-3">
                {top.map((item, index) => {
                  const name = localizedText(item.name, item.nameTranslations, locale) ?? `#${item.functionId}`;
                  return (
                    <li key={item.functionId} className="grid grid-cols-[1.5rem_minmax(0,1fr)] gap-x-3">
                      <span className="text-sm font-semibold leading-6 tabular-nums text-secondary">{index + 1}</span>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
                          <span className="flex min-w-0 flex-wrap items-center gap-2">
                            <span className="font-medium text-foreground wrap-anywhere">{name}</span>
                            {item.status && item.status !== 'PUBLISHED' && <FunctionStatusBadge status={item.status} />}
                          </span>
                          <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                            <span className="tabular-nums text-foreground">
                              {t('analytics.views', 'Views:')} <span className="font-semibold">{number.format(item.views)}</span>
                            </span>
                            <span className="tabular-nums text-secondary">
                              {t('analytics.actions', 'Link clicks and prints:')} {number.format(item.actions)}
                            </span>
                            {item.functionId !== null && item.status === 'PUBLISHED' && (
                              <Link
                                to={`/functions/${item.functionId}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1 rounded-sm text-accent-text underline-offset-4 fine:hover:underline"
                              >
                                <ExternalLinkIcon className="h-3.5 w-3.5" aria-hidden="true" />
                                {t('reports.openPublic', 'Open public page')}
                                <span className="sr-only">: {name}</span>
                              </Link>
                            )}
                            {item.functionId !== null && hasPermission('FUNCTIONS_VIEW') && (
                              <Link
                                to={`/admin/functions/${item.functionId}`}
                                className="inline-flex items-center gap-1 rounded-sm text-accent-text underline-offset-4 fine:hover:underline"
                              >
                                <PencilIcon className="h-3.5 w-3.5" aria-hidden="true" />
                                {t('reports.openEditor', 'Open in editor')}
                                <span className="sr-only">: {name}</span>
                              </Link>
                            )}
                          </span>
                        </div>
                        <div className="mt-2 h-1.5 overflow-hidden rounded-pill bg-surface-2" aria-hidden="true">
                          <div className="h-full rounded-pill bg-chart-mark" style={{ width: `${Math.round((item.views / topViews) * 100)}%` }} />
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ol>
            )}
          </section>
        </CardBody>
      )}
    </Card>
  );
}
