import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { ColumnDef } from '@tanstack/react-table';
import { UsersIcon } from 'lucide-react';
import { FunctionStatusBadge, StatusPill } from '../../components/ui/Badge';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { Card, CardHeader } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Select } from '../../components/ui/Select';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { ASSIGN_PERMISSION } from '../../components/functions/FunctionAssignee';
import { useAssignmentOverview } from '../functions/queries';
import { InlineAlert } from '../functions/InlineAlert';
import { ASSIGNMENT_STATES, type AssignmentItem, type AssignmentState } from '../../types/analytics';
import { localizedText } from '../../utils/translations';
import { assignmentStateLabel } from './assignmentLabels';

const tones = { ASSIGNED: 'published', UNASSIGNED: 'draft', NEEDS_REASSIGNMENT: 'danger', UNKNOWN: 'pending' } as const;

interface AssignmentsSectionProps {
  organizationId?: number;
  organizationName: (id: number | null) => string;
  showOrganization: boolean;
}

/** Who keeps each card current; cards without anybody and cards whose assignee lost access are listed first. */
export function AssignmentsSection({ organizationId, organizationName, showOrganization }: AssignmentsSectionProps) {
  const { t, locale } = useI18n();
  const { user, hasPermission } = useAuth();
  const [state, setState] = useState<AssignmentState | ''>('');
  const overview = useAssignmentOverview(organizationId, state || undefined);
  const canOpenEditor = hasPermission('FUNCTIONS_VIEW') || hasPermission(ASSIGN_PERMISSION);
  const counts = overview.data?.counts;

  const columns = useMemo<ColumnDef<AssignmentItem>[]>(
    () => [
      {
        id: 'service',
        header: t('reports.service', 'Service'),
        accessorFn: (row) => row.name,
        cell: ({ row }) => (
          <div className="min-w-0 max-w-sm">
            <span className="block font-medium text-foreground wrap-anywhere">{localizedText(row.original.name, row.original.nameTranslations, locale)}</span>
            <span className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-secondary">
              <FunctionStatusBadge status={row.original.status} />
              <span className="tabular-nums">#{row.original.functionId}</span>
              {showOrganization && <span>· {organizationName(row.original.organizationId)}</span>}
            </span>
          </div>
        ),
      },
      {
        id: 'assignee',
        header: t('assignee.title', 'Responsible staff member'),
        accessorFn: (row) => row.assignee?.name ?? '',
        cell: ({ row }) =>
          row.original.assignee ? (
            <span className="block wrap-anywhere">
              {row.original.assignee.name ?? row.original.assignee.email}
              {row.original.assignee.userId === user?.id && <span className="ms-1 text-secondary">({t('verification.byYou', 'you')})</span>}
              {row.original.assignee.email && <span className="block text-xs text-secondary">{row.original.assignee.email}</span>}
            </span>
          ) : (
            <span className="text-secondary">—</span>
          ),
      },
      {
        id: 'state',
        header: t('field.status'),
        accessorFn: (row) => row.state,
        cell: ({ row }) => <StatusPill tone={tones[row.original.state]}>{assignmentStateLabel(row.original.state, t)}</StatusPill>,
      },
      {
        id: 'actions',
        header: t('field.actions'),
        enableSorting: false,
        meta: { align: 'right', hideBelow: 'md' },
        cell: ({ row }) =>
          canOpenEditor ? (
            <Link to={`/admin/functions/${row.original.functionId}#function-assignee`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
              {row.original.state === 'ASSIGNED' ? t('assignee.reassign', 'Reassign') : t('assignee.assign', 'Assign')}
            </Link>
          ) : null,
      },
    ],
    [t, locale, user?.id, organizationName, showOrganization, canOpenEditor],
  );

  return (
    <section id="assignments" aria-label={t('assignments.title', 'Responsible staff')} className="scroll-mt-24">
      <Card>
        <CardHeader
          icon={<UsersIcon />}
          title={t('assignments.title', 'Responsible staff')}
          description={t('assignments.subtitle', 'Each card should have an active staff member who keeps it current. Cards without one, or whose person lost access, are listed first.')}
        />
        <div className="grid gap-3 border-b border-line px-5 py-4 sm:grid-cols-[14rem_minmax(0,1fr)] sm:items-center">
          <Select
            value={state}
            aria-label={t('field.status')}
            size="sm"
            onValueChange={(value) => setState(value as AssignmentState | '')}
            options={[
              { value: '', label: t('status.all') },
              ...ASSIGNMENT_STATES.filter((value) => value !== 'UNKNOWN').map((value) => ({ value, label: assignmentStateLabel(value, t) })),
            ]}
          />
          {counts && (
            <p className="text-sm text-secondary" aria-live="polite">
              <span className="tabular-nums">{t('assignments.assignedCount', { count: counts.assigned })}</span> ·{' '}
              <span className="tabular-nums">{t('assignments.unassignedCount', { count: counts.unassigned })}</span> ·{' '}
              <span className="tabular-nums font-semibold text-foreground">{t('assignments.reassignCount', { count: counts.needsReassignment })}</span>
            </p>
          )}
        </div>
        {overview.data && !overview.data.membersChecked && (
          <div className="border-b border-line px-5 py-3">
            <InlineAlert tone="warning">{t('assignments.membersUnavailable', 'The staff directory could not be reached, so it is unknown whether every assignee is still active.')}</InlineAlert>
          </div>
        )}
        {counts && counts.needsReassignment > 0 && (
          <div className="border-b border-line px-5 py-3">
            <InlineAlert tone="danger">{t('assignments.reassignNotice', 'Some assignees were deactivated or lost access to the organization. Nobody was reassigned automatically; choose a new person for each card.')}</InlineAlert>
          </div>
        )}
        <DataTable
          columns={columns}
          data={overview.data?.items ?? []}
          rowKey={(row) => row.functionId}
          caption={t('assignments.title', 'Responsible staff')}
          loading={overview.isPending}
          error={overview.error}
          onRetry={() => void overview.refetch()}
          pageSize={10}
          empty={{ title: t('assignments.emptyTitle', 'No cards in this view'), description: t('assignments.emptyText', 'Every card here has an active responsible staff member.') }}
        />
      </Card>
    </section>
  );
}
