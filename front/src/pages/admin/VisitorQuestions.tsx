import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { ColumnDef } from '@tanstack/react-table';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge, StatusPill } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Select } from '../../components/ui/Select';
import { Toolbar } from '../../components/ui/Toolbar';
import { useI18n } from '../../contexts/i18n';
import { parseId, useScopeOptions } from '../../features/analytics/scope';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { HELP_STATUS_FILTERS, helpStatusFilterLabel, helpStatusLabel, helpStatusTones } from '../../features/help/helpOptions';
import { LiveStatus } from '../../features/help/LiveStatus';
import { INBOX_POLL_MS } from '../../features/help/liveSupport';
import { useHelpInbox } from '../../features/help/queries';
import { QuestionReviewDialog } from '../../features/help/QuestionReviewDialog';
import type { HelpConversationSummary, HelpInboxQuery, HelpStatusFilter } from '../../types/help';
import { formatDateTime } from '../../utils/format';
import { usePatchSearchParams } from '../../hooks/usePatchSearchParams';

const LIST_LIMIT = 500;

function parseStatus(value: string | null): HelpStatusFilter {
  return HELP_STATUS_FILTERS.includes(value as HelpStatusFilter) ? (value as HelpStatusFilter) : 'ACTIVE';
}

/** Support inbox; the backend limits it to the caller's organizations (central conversations: global staff only). */
export function VisitorQuestions() {
  const { t, locale } = useI18n();
  const scope = useScopeOptions();
  const [params, setParam] = usePatchSearchParams();
  const [selected, setSelected] = useState<HelpConversationSummary | null>(null);

  const query = useMemo<HelpInboxQuery>(() => ({
    status: parseStatus(params.get('status')),
    organizationId: params.get('general') === 'true' ? undefined : parseId(params.get('organization')),
    general: scope.global && params.get('general') === 'true' ? true : undefined,
    unread: params.get('unread') === 'true' ? true : undefined,
    sort: params.get('sort') === 'UNREAD_FIRST' ? 'UNREAD_FIRST' : undefined,
    assignee: params.get('assignee') === 'me' ? 'me' : undefined,
  }), [params, scope.global]);
  const inbox = useHelpInbox(query);

  const columns = useMemo<ColumnDef<HelpConversationSummary>[]>(() => [
    {
      id: 'conversation',
      header: t('support.lastMessage', 'Last message'),
      accessorFn: (row) => row.lastMessageAt ?? row.updatedAt,
      cell: ({ row }) => (
        <div className="min-w-0 max-w-xl">
          <span className="flex flex-wrap items-center gap-2">
            <span className={row.original.unreadCount > 0 ? 'font-semibold text-foreground' : 'font-medium text-foreground'}>
              {row.original.general ? t('support.generalContext', 'General support') : row.original.serviceName ?? scope.organizationName(row.original.organizationId)}
            </span>
            {row.original.unreadCount > 0 && <Badge size="sm" tone="accent">{row.original.unreadCount} {t('support.unread', 'unread')}</Badge>}
          </span>
          {row.original.lastMessagePreview && (
            <span className="mt-1 block truncate text-sm text-secondary">
              {row.original.lastMessageAuthor === 'STAFF' ? `${t('support.you', 'You')}: ` : ''}{row.original.lastMessagePreview}
            </span>
          )}
          <span className="mt-1 flex flex-wrap gap-1.5 text-xs text-secondary tabular-nums">
            <span>#{row.original.id}</span>
            {row.original.lastMessageAt && <span>· {formatDateTime(row.original.lastMessageAt, locale)}</span>}
            {!row.original.general && scope.showOrganizations && <span>· {scope.organizationName(row.original.organizationId)}</span>}
          </span>
        </div>
      ),
    },
    {
      accessorKey: 'assignedUserName',
      header: t('support.assignee', 'Responsible staff member'),
      enableSorting: false,
      meta: { hideBelow: 'md' },
      cell: ({ row }) => <span className="text-secondary">{row.original.assignedUserName ?? t('support.unassigned', 'Nobody assigned')}</span>,
    },
    {
      accessorKey: 'status',
      header: t('field.status'),
      cell: ({ row }) => <StatusPill tone={helpStatusTones[row.original.status]}>{helpStatusLabel(row.original.status, t)}</StatusPill>,
    },
    {
      id: 'actions',
      header: t('field.actions'),
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => (
        <Button variant="outline" size="sm" onClick={() => setSelected(row.original)}
          aria-label={`${t('support.open', 'Open')} #${row.original.id}`}>
          {t('support.open', 'Open')}
        </Button>
      ),
    },
  ], [t, locale, scope]);

  const rows = inbox.data ?? [];
  const filtered = params.toString() !== '';

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('support.inboxTitle', 'Support inbox')}
        description={t('support.inboxSubtitleLive', 'Visitors’ support conversations about your organization’s services. Reply in the same thread; visitors see the reply on their Support page. New messages appear automatically.')}
      />
      <Card>
        <div className="grid gap-3 border-b border-line px-5 py-4 sm:grid-cols-2 lg:grid-cols-5">
          <Select value={query.status ?? 'ACTIVE'} aria-label={t('field.status')} size="sm"
            onValueChange={(value) => setParam({ status: value === 'ACTIVE' ? '' : value })}
            options={HELP_STATUS_FILTERS.map((value) => ({ value, label: helpStatusFilterLabel(value, t) }))} />
          <Select value={query.unread ? 'true' : ''} aria-label={t('support.readState', 'Read state')} size="sm"
            onValueChange={(value) => setParam({ unread: value })}
            options={[{ value: '', label: t('support.anyReadState', 'Read and unread') }, { value: 'true', label: t('support.unreadOnly', 'Only with unread messages') }]} />
          {scope.showOrganizations && (
            <Select
              value={params.get('general') === 'true' ? 'general' : query.organizationId ? String(query.organizationId) : ''}
              aria-label={t('field.organization')} size="sm"
              onValueChange={(value) => setParam({ general: value === 'general' ? 'true' : '', organization: value === 'general' ? '' : value })}
              options={[...scope.organizations, ...(scope.global ? [{ value: 'general', label: t('support.generalOnly', 'General support (central)') }] : [])]}
            />
          )}
          <Select value={query.assignee ?? ''} aria-label={t('support.assignee', 'Responsible staff member')} size="sm"
            onValueChange={(value) => setParam({ assignee: value })}
            options={[{ value: '', label: t('reports.anyAssignee', 'Any responsible staff member') }, { value: 'me', label: t('support.assignedToMe', 'Assigned to me') }]} />
          <Select value={query.sort ?? 'LAST_MESSAGE'} aria-label={t('support.sort', 'Sort')} size="sm"
            onValueChange={(value) => setParam({ sort: value === 'LAST_MESSAGE' ? '' : value })}
            options={[{ value: 'LAST_MESSAGE', label: t('support.sortLast', 'Newest message first') }, { value: 'UNREAD_FIRST', label: t('support.sortUnread', 'Unread first') }]} />
        </div>
        <Toolbar summary={`${rows.length} ${t('home.resultsCount')}`}>
          <LiveStatus channel="staff" pollSeconds={INBOX_POLL_MS / 1000} />
          {filtered && <Link to="/admin/questions" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>{t('action.reset', 'Reset')}</Link>}
          {scope.global && <Badge size="sm">{t('support.globalScope', 'You see every organization and central support')}</Badge>}
        </Toolbar>
        {rows.length >= LIST_LIMIT && (
          <div className="border-b border-line px-5 py-3">
            <InlineAlert tone="warning">{t('support.limitNotice', 'Showing the 500 conversations with the newest messages. Use the filters to narrow the list.')}</InlineAlert>
          </div>
        )}
        <DataTable
          columns={columns}
          data={rows}
          rowKey={(row) => row.id}
          caption={t('support.inboxTitle', 'Support inbox')}
          loading={inbox.isPending}
          error={inbox.error}
          onRetry={() => void inbox.refetch()}
          pageSize={25}
          empty={{ title: t('support.emptyInboxTitle', 'No conversations here'), description: t('support.emptyInboxText', 'Visitor messages appear here as soon as they are sent.') }}
        />
      </Card>
      <QuestionReviewDialog conversation={selected} organizationName={scope.organizationName} onClose={() => setSelected(null)} />
    </div>
  );
}
