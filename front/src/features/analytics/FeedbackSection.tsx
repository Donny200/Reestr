import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import type { ColumnDef } from '@tanstack/react-table';
import { MessageSquareHeartIcon } from 'lucide-react';
import { FunctionStatusBadge } from '../../components/ui/Badge';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import type { AnalyticsFilters, ServiceFeedback } from '../../types/analytics';
import { localizedText } from '../../utils/translations';
import { InlineAlert } from '../functions/InlineAlert';
import { MetricTile } from './MetricTile';
import { useFeedbackReport } from './queries';

interface FeedbackSectionProps {
  filters: AnalyticsFilters;
  organizationName: (id: number | null) => string;
  showOrganization: boolean;
}

/** Aggregate "Was this information helpful?" answers; usefulness of the text, never a rating of anybody. */
export function FeedbackSection({ filters, organizationName, showOrganization }: FeedbackSectionProps) {
  const { t, locale } = useI18n();
  const { hasPermission } = useAuth();
  const report = useFeedbackReport(filters);
  const percent = useMemo(() => new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }), [locale]);
  const data = report.data;
  const share = data && data.totals.total > 0 ? data.totals.helpful / data.totals.total : null;

  const columns = useMemo<ColumnDef<ServiceFeedback>[]>(
    () => [
      {
        id: 'service',
        header: t('reports.service', 'Service'),
        accessorFn: (row) => row.name ?? '',
        cell: ({ row }) => (
          <div className="min-w-0 max-w-sm">
            {hasPermission('FUNCTIONS_VIEW') ? (
              <Link to={`/admin/functions/${row.original.functionId}`} className="rounded-sm font-medium text-foreground underline-offset-4 wrap-anywhere fine:hover:underline">
                {localizedText(row.original.name, row.original.nameTranslations, locale) ?? `#${row.original.functionId}`}
              </Link>
            ) : (
              <span className="font-medium text-foreground wrap-anywhere">{localizedText(row.original.name, row.original.nameTranslations, locale) ?? `#${row.original.functionId}`}</span>
            )}
            <span className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-secondary">
              {row.original.status && <FunctionStatusBadge status={row.original.status} />}
              {showOrganization && <span>· {organizationName(row.original.organizationId)}</span>}
            </span>
          </div>
        ),
      },
      { accessorKey: 'helpful', header: t('feedback.helpful', 'Helpful'), cell: ({ row }) => <span className="tabular-nums">{row.original.helpful}</span> },
      { accessorKey: 'notHelpful', header: t('feedback.notHelpful', 'Not helpful'), cell: ({ row }) => <span className="tabular-nums">{row.original.notHelpful}</span> },
      {
        id: 'share',
        header: t('feedback.share', 'Helpful share'),
        accessorFn: (row) => (row.total === 0 ? 0 : row.helpful / row.total),
        cell: ({ row }) => <span className="tabular-nums">{row.original.total === 0 ? '—' : percent.format(row.original.helpful / row.original.total)}</span>,
      },
    ],
    [t, locale, hasPermission, organizationName, showOrganization, percent],
  );

  return (
    <section id="feedback" aria-label={t('feedback.sectionTitle', 'Was this information helpful?')} className="scroll-mt-24">
      <Card>
        <CardHeader
          icon={<MessageSquareHeartIcon />}
          title={t('feedback.sectionTitle', 'Was this information helpful?')}
          description={t('feedback.sectionHint', 'Anonymous yes/no answers from public service pages for the selected period.')}
        />
        <CardBody className="space-y-5">
          <InlineAlert tone="info">
            {t('feedback.disclaimer', 'These counts measure whether visitors found the published text useful. They are not a rating of the organization, the government service or its staff, and one browser may answer more than once.')}
          </InlineAlert>
          <div className="grid gap-3 min-[420px]:grid-cols-2 lg:grid-cols-4">
            <MetricTile label={t('feedback.answers', 'Answers')} value={data?.totals.total ?? 0} previous={data?.previousTotals.total ?? 0} loading={report.isPending} />
            <MetricTile label={t('feedback.helpful', 'Helpful')} value={data?.totals.helpful ?? 0} previous={data?.previousTotals.helpful ?? 0} loading={report.isPending} />
            <MetricTile label={t('feedback.notHelpful', 'Not helpful')} value={data?.totals.notHelpful ?? 0} previous={data?.previousTotals.notHelpful ?? 0} loading={report.isPending} />
            <div className="rounded-card-sm border border-line bg-background p-4">
              <p className="micro text-secondary">{t('feedback.share', 'Helpful share')}</p>
              <p className="mt-3 text-[1.75rem] font-semibold leading-none tabular-nums text-foreground">{report.isPending ? '…' : share === null ? '—' : percent.format(share)}</p>
              <p className="mt-3 text-xs text-secondary">{t('feedback.shareHint', 'Share of “yes” among all answers')}</p>
            </div>
          </div>
          <DataTable
            columns={columns}
            data={data?.services ?? []}
            rowKey={(row) => row.functionId}
            caption={t('feedback.sectionTitle', 'Was this information helpful?')}
            loading={report.isPending}
            error={report.error}
            onRetry={() => void report.refetch()}
            pageSize={10}
            initialSorting={[{ id: 'helpful', desc: true }]}
            empty={{ title: t('feedback.emptyTitle', 'No answers in this period'), description: t('feedback.emptyText', 'Answers appear once visitors use the question on public service pages.') }}
          />
        </CardBody>
      </Card>
    </section>
  );
}
