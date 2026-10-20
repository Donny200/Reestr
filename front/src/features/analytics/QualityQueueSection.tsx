import { forwardRef, useMemo } from 'react';
import { Link } from 'react-router-dom';
import type { ColumnDef } from '@tanstack/react-table';
import { PencilIcon } from 'lucide-react';
import { FunctionStatusBadge } from '../../components/ui/Badge';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { Card, CardHeader } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Select } from '../../components/ui/Select';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { exportQualityQueue } from '../../services/analyticsService';
import { FUNCTION_STATUSES, functionStatusLabel, type FunctionStatus } from '../../types/adminFunctions';
import { QUALITY_ISSUE_TYPES, type QualityIssueType, type QualityQueueItem } from '../../types/analytics';
import { formatDate } from '../../utils/format';
import { localizedText } from '../../utils/translations';
import { InlineAlert } from '../functions/InlineAlert';
import { ExportButton } from './ExportButton';
import { useQualityQueue } from './queries';
import { editorPath, issueGuidance, issueLabel } from './qualityIssues';

interface QualityQueueSectionProps {
  organizationId?: number;
  categoryId?: number;
  issue: QualityIssueType | '';
  status: FunctionStatus | '';
  onIssueChange: (issue: QualityIssueType | '') => void;
  onStatusChange: (status: FunctionStatus | '') => void;
  organizationName: (id: number | null) => string;
  showOrganization: boolean;
}

export const QualityQueueSection = forwardRef<HTMLDivElement, QualityQueueSectionProps>(function QualityQueueSection(
  { organizationId, categoryId, issue, status, onIssueChange, onStatusChange, organizationName, showOrganization },
  ref,
) {
  const { t, locale, available } = useI18n();
  const { hasPermission } = useAuth();
  const filters = { organizationId, categoryId, issue: issue || undefined, status: status || undefined };
  const queue = useQualityQueue(filters);
  const canOpenEditor = hasPermission('FUNCTIONS_VIEW');
  const languageName = useMemo(() => {
    const names = new Map(available.map((language) => [language.code.toLowerCase(), language.label]));
    return (code: string) => names.get(code.toLowerCase()) ?? code.toUpperCase();
  }, [available]);

  const columns = useMemo<ColumnDef<QualityQueueItem>[]>(
    () => [
      {
        id: 'service',
        header: t('reports.service', 'Service'),
        accessorFn: (row) => row.name,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="min-w-0 max-w-sm">
              <span className="block font-medium text-foreground wrap-anywhere">
                {localizedText(item.name, item.nameTranslations, locale)}
              </span>
              <span className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-secondary">
                <FunctionStatusBadge status={item.status} />
                <span className="tabular-nums">#{item.functionId}</span>
                {showOrganization && <span>· {organizationName(item.organizationId)}</span>}
                {item.category && <span>· {item.category}</span>}
              </span>
            </div>
          );
        },
      },
      {
        id: 'issues',
        header: t('quality.whatToFix', 'What to fix'),
        enableSorting: false,
        cell: ({ row }) => (
          <ul className="min-w-0 max-w-xl space-y-2">
            {row.original.issues.map((found) => (
              <li key={found.type} className="text-sm">
                <span className="font-medium text-foreground">{issueLabel(found.type, t)}</span>
                <span className="block text-secondary wrap-anywhere">{issueGuidance(found, t, languageName)}</span>
                {found.type === 'VERIFICATION_OVERDUE' && row.original.lastVerifiedAt && (
                  <span className="block text-xs tabular-nums text-secondary">
                    {t('quality.lastVerified', 'Last verified:')} {formatDate(row.original.lastVerifiedAt, locale)}
                  </span>
                )}
                {canOpenEditor && (
                  <Link
                    to={editorPath(row.original.functionId, found.type)}
                    className="mt-1 inline-flex items-center gap-1 rounded-sm text-xs font-medium text-accent-text underline-offset-4 fine:hover:underline"
                  >
                    {t('quality.fixInEditor', 'Fix in editor')}
                    <span className="sr-only">: {issueLabel(found.type, t)}</span>
                  </Link>
                )}
              </li>
            ))}
          </ul>
        ),
      },
      {
        id: 'actions',
        header: t('field.actions'),
        enableSorting: false,
        meta: { align: 'right', hideBelow: 'md' },
        cell: ({ row }) =>
          canOpenEditor ? (
            <Link to={`/admin/functions/${row.original.functionId}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
              <PencilIcon aria-hidden="true" />
              {t('reports.openEditor', 'Open in editor')}
            </Link>
          ) : (
            <span className="block max-w-[12rem] text-xs text-secondary">{t('quality.editorAccessNeeded', 'Changing the card requires service editor access.')}</span>
          ),
      },
    ],
    [t, locale, canOpenEditor, languageName, organizationName, showOrganization],
  );

  return (
    <div ref={ref} id="quality-queue" tabIndex={-1} className="scroll-mt-24 focus:outline-none">
      <Card>
        <CardHeader
          title={t('quality.title', 'Service cards that need attention')}
          description={t('quality.subtitle', 'Current state of non-deactivated cards. Fixing a card removes it from the queue on the next load.')}
          actions={<ExportButton label={t('export.csv', 'Export CSV')} run={(delimiter) => exportQualityQueue(filters, delimiter)} />}
        />
        <div className="grid gap-3 border-b border-line px-5 py-4 sm:grid-cols-2 lg:grid-cols-4">
          <Select
            value={issue}
            aria-label={t('quality.issueFilter', 'Issue')}
            size="sm"
            onValueChange={(value) => onIssueChange(value as QualityIssueType | '')}
            options={[
              { value: '', label: t('quality.allIssues', 'All issues') },
              ...QUALITY_ISSUE_TYPES.map((type) => ({ value: type, label: issueLabel(type, t) })),
            ]}
          />
          <Select
            value={status}
            aria-label={t('field.status')}
            size="sm"
            onValueChange={(value) => onStatusChange(value as FunctionStatus | '')}
            options={[
              { value: '', label: `${t('field.status')} · ${t('status.all')}` },
              ...FUNCTION_STATUSES.filter((value) => value !== 'DEACTIVATED').map((value) => ({
                value,
                label: functionStatusLabel(value, t),
              })),
            ]}
          />
        </div>
        {queue.data && !queue.data.translationsChecked && (
          <div className="border-b border-line px-5 py-3">
            <InlineAlert tone="warning">
              {t('quality.languagesUnavailable', 'Translation gaps could not be checked because the list of active languages is unavailable. Other checks are complete.')}
            </InlineAlert>
          </div>
        )}
        <DataTable
          columns={columns}
          data={queue.data?.items ?? []}
          rowKey={(row) => row.functionId}
          caption={t('quality.title', 'Service cards that need attention')}
          loading={queue.isPending}
          error={queue.error}
          onRetry={() => void queue.refetch()}
          pageSize={10}
          empty={{
            title: t('quality.emptyTitle', 'No cards need attention'),
            description: t('quality.emptyText', 'Every card in this view has a current verification, an official source, the required fields and all translations.'),
          }}
        />
      </Card>
    </div>
  );
});
