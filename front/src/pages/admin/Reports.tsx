import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { ColumnDef } from '@tanstack/react-table';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge, StatusPill } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Toolbar } from '../../components/ui/Toolbar';
import { useI18n } from '../../contexts/i18n';
import { ExportButton } from '../../features/analytics/ExportButton';
import { isIsoDate } from '../../features/analytics/period';
import { parseId, useScopeOptions } from '../../features/analytics/scope';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { useReports } from '../../features/reports/queries';
import { ReportReviewDialog } from '../../features/reports/ReportReviewDialog';
import {
  ALL_REPORT_CATEGORIES,
  REPORT_STATUS_FILTERS,
  reportCategoryLabel,
  reportStatusFilterLabels,
  reportStatusLabel,
  reportStatusTones,
  reportTypeLabel,
} from '../../features/reports/reportOptions';
import { exportReports, exportReportsDocument } from '../../services/reportService';
import type { InformationReport, ReportCategory, ReportEntityType, ReportQuery, ReportStatusFilter } from '../../types/reports';
import { formatDateTime } from '../../utils/format';
import { usePatchSearchParams } from '../../hooks/usePatchSearchParams';

const LIST_LIMIT = 500;

function parseStatus(value: string | null): ReportStatusFilter {
  return REPORT_STATUS_FILTERS.includes(value as ReportStatusFilter) ? (value as ReportStatusFilter) : 'OPEN';
}

function parseEntityType(value: string | null): ReportEntityType | undefined {
  return value === 'FUNCTION' || value === 'ORGANIZATION' ? value : undefined;
}

function parseCategory(value: string | null): ReportCategory | undefined {
  return ALL_REPORT_CATEGORIES.includes(value as ReportCategory) ? (value as ReportCategory) : undefined;
}

export function Reports() {
  const { t, locale } = useI18n();
  const scope = useScopeOptions();
  const [searchParams, patchParams] = usePatchSearchParams();
  const [selected, setSelected] = useState<InformationReport | null>(null);

  const query = useMemo<ReportQuery>(() => {
    const entityId = parseId(searchParams.get('entityId'));
    const from = searchParams.get('from');
    const to = searchParams.get('to');
    return {
      status: parseStatus(searchParams.get('status')),
      category: parseCategory(searchParams.get('category')),
      entityType: entityId ? (parseEntityType(searchParams.get('entityType')) ?? 'FUNCTION') : parseEntityType(searchParams.get('entityType')),
      entityId,
      serviceCategoryId: parseId(searchParams.get('serviceCategory')),
      from: isIsoDate(from) ? from : undefined,
      to: isIsoDate(to) ? to : undefined,
      organizationId: parseId(searchParams.get('organization')),
      assignee: searchParams.get('assignee') === 'me' ? 'me' : undefined,
    };
  }, [searchParams]);
  const invalidRange = Boolean(query.from && query.to && query.from > query.to);

  const reports = useReports(query, !invalidRange);
  const services = useReports({ status: 'ALL', entityType: 'FUNCTION', organizationId: query.organizationId });

  const setParam = (name: string, value: string) =>
    patchParams({
      [name]: value,
      ...(name === 'entityId' && { entityType: '' }),
      ...((name === 'entityType' || name === 'organization') && { entityId: '' }),
    });

  const serviceOptions = useMemo(() => {
    const seen = new Map<number, string>();
    (services.data ?? []).forEach((item) => {
      if (!seen.has(item.entityId)) seen.set(item.entityId, item.entityLabel);
    });
    if (query.entityId && query.entityType === 'FUNCTION' && !seen.has(query.entityId)) seen.set(query.entityId, `#${query.entityId}`);
    return [
      { value: '', label: t('reports.allServices', 'All services') },
      ...[...seen.entries()].sort((a, b) => a[1].localeCompare(b[1], locale)).map(([id, label]) => ({ value: String(id), label: `${label} · #${id}` })),
    ];
  }, [services.data, query.entityId, query.entityType, t, locale]);

  const columns = useMemo<ColumnDef<InformationReport>[]>(
    () => [
      {
        accessorKey: 'createdAt',
        header: t('reports.received', 'Received'),
        cell: ({ row }) => <span className="tabular-nums text-secondary">{formatDateTime(row.original.createdAt, locale)}</span>,
      },
      {
        id: 'subject',
        header: t('reports.subject', 'Subject'),
        accessorFn: (row) => row.entityLabel,
        cell: ({ row }) => (
          <div className="min-w-0 max-w-md">
            <span className="block font-medium text-foreground wrap-anywhere">{row.original.entityLabel}</span>
            <span className="mt-1 flex flex-wrap items-center gap-1.5">
              <Badge size="sm">{reportTypeLabel(row.original.entityType, t)}</Badge>
              <span className="text-xs tabular-nums text-secondary">#{row.original.entityId}</span>
              {scope.showOrganizations && <span className="text-xs text-secondary">· {scope.organizationName(row.original.organizationId)}</span>}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'category',
        header: t('reports.category', 'Problem'),
        enableSorting: false,
        meta: { hideBelow: 'md' },
        cell: ({ row }) => (
          <span className="text-secondary">{reportCategoryLabel(row.original.category, t)}</span>
        ),
      },
      {
        accessorKey: 'status',
        header: t('field.status'),
        cell: ({ row }) => <StatusPill tone={reportStatusTones[row.original.status]}>{reportStatusLabel(row.original.status, t)}</StatusPill>,
      },
      {
        id: 'actions',
        header: t('field.actions'),
        enableSorting: false,
        meta: { align: 'right' },
        cell: ({ row }) => (
          <Button variant="outline" size="sm" onClick={() => setSelected(row.original)}>
            {t('reports.review', 'Review')}
          </Button>
        ),
      },
    ],
    [t, locale, scope],
  );

  const rows = invalidRange ? [] : (reports.data ?? []);
  const filtered = searchParams.toString() !== '';

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('reports.title', 'Information reports')}
        description={t('reports.subtitle', 'Visitor reports about incorrect or outdated information. Reports are private and never change public pages automatically.')}
        actions={
          <div className="flex flex-wrap gap-2">
            <ExportButton label={t('export.csv', 'Export CSV')} disabled={invalidRange} run={(delimiter) => exportReports(query, delimiter)} />
            <ExportButton label={t('export.xlsx', 'Export Excel')} disabled={invalidRange} run={() => exportReportsDocument(query, 'XLSX', locale)} />
            <ExportButton label={t('export.pdf', 'Export PDF')} disabled={invalidRange} run={() => exportReportsDocument(query, 'PDF', locale)} />
          </div>
        }
      />
      <Card>
        <div className="grid gap-3 border-b border-line px-5 py-4 sm:grid-cols-2 lg:grid-cols-4">
          <Select
            value={query.status}
            aria-label={t('field.status')}
            size="sm"
            onValueChange={(value) => setParam('status', value === 'OPEN' ? '' : value)}
            options={REPORT_STATUS_FILTERS.map((value) => ({
              value,
              label: value === 'OPEN' || value === 'ALL' ? t(`reports.filter.${value}`, reportStatusFilterLabels[value]) : reportStatusLabel(value, t),
            }))}
          />
          <Select
            value={query.category ?? ''}
            aria-label={t('reports.category', 'Problem')}
            size="sm"
            onValueChange={(value) => setParam('category', value)}
            options={[
              { value: '', label: t('reports.allProblems', 'All problem types') },
              ...ALL_REPORT_CATEGORIES.map((value) => ({ value, label: reportCategoryLabel(value, t) })),
            ]}
          />
          <Select
            value={query.entityId && query.entityType === 'FUNCTION' ? String(query.entityId) : ''}
            aria-label={t('reports.service', 'Service')}
            size="sm"
            onValueChange={(value) => setParam('entityId', value)}
            options={serviceOptions}
          />
          <Select
            value={query.serviceCategoryId ? String(query.serviceCategoryId) : ''}
            aria-label={t('analytics.category', 'Service category')}
            size="sm"
            onValueChange={(value) => setParam('serviceCategory', value)}
            options={scope.categories}
          />
          <Select
            value={query.entityType ?? ''}
            aria-label={t('reports.type', 'Type')}
            size="sm"
            onValueChange={(value) => setParam('entityType', value)}
            options={[
              { value: '', label: `${t('reports.type', 'Type')} · ${t('status.all')}` },
              { value: 'FUNCTION', label: reportTypeLabel('FUNCTION', t) },
              { value: 'ORGANIZATION', label: reportTypeLabel('ORGANIZATION', t) },
            ]}
          />
          {scope.showOrganizations && (
            <Select
              value={query.organizationId ? String(query.organizationId) : ''}
              aria-label={t('field.organization')}
              size="sm"
              onValueChange={(value) => setParam('organization', value)}
              options={scope.organizations}
            />
          )}
          <Select
            value={query.assignee ?? ''}
            aria-label={t('assignee.title', 'Responsible staff member')}
            size="sm"
            onValueChange={(value) => setParam('assignee', value)}
            options={[
              { value: '', label: t('reports.anyAssignee', 'Any responsible staff member') },
              { value: 'me', label: t('reports.assignedToMe', 'Cards assigned to me') },
            ]}
          />
          <Field label={t('analytics.from', 'From')} error={invalidRange ? t('analytics.rangeOrder', 'The start date must not be after the end date') : undefined}>
            {(control) => <Input {...control} type="date" className="min-h-10 py-2 text-sm" value={query.from ?? ''} onChange={(event) => setParam('from', event.target.value)} />}
          </Field>
          <Field label={t('analytics.to', 'To')}>
            {(control) => <Input {...control} type="date" className="min-h-10 py-2 text-sm" value={query.to ?? ''} onChange={(event) => setParam('to', event.target.value)} />}
          </Field>
        </div>
        <Toolbar summary={t('home.resultsCount', { count: rows.length })}>
          {filtered && (
            <Link to="/admin/reports" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
              {t('action.reset', 'Reset')}
            </Link>
          )}
        </Toolbar>
        {rows.length >= LIST_LIMIT && (
          <div className="border-b border-line px-5 py-3">
            <InlineAlert tone="warning">{t('reports.limitNotice', 'Showing the newest 500 reports. Use the filters to narrow the list.')}</InlineAlert>
          </div>
        )}
        <DataTable
          columns={columns}
          data={rows}
          rowKey={(row) => row.id}
          caption={t('reports.title', 'Information reports')}
          loading={!invalidRange && reports.isPending}
          error={reports.error}
          onRetry={() => void reports.refetch()}
          pageSize={25}
          empty={{ title: t('reports.emptyTitle', 'No reports here'), description: t('reports.emptyText', 'New reports from visitors will appear in this list.') }}
        />
      </Card>

      <ReportReviewDialog report={selected} onClose={() => setSelected(null)} />
    </div>
  );
}
