import { useMemo, useState } from 'react';
import type { ColumnDef } from '@tanstack/react-table';
import { ArrowUpCircleIcon, KeyRoundIcon, MoreHorizontalIcon, PencilIcon, PlusIcon, PowerOffIcon } from 'lucide-react';
import { toast } from 'sonner';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';
import { SearchInput } from '../../components/ui/SearchInput';
import { Toolbar } from '../../components/ui/Toolbar';
import { DataTable } from '../../components/ui/DataTable';
import { Avatar } from '../../components/ui/Avatar';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { ConfirmModal } from '../../components/ui/Modal';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '../../components/ui/DropdownMenu';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import type { Organization, StaffUser } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { matchesStatus, statusFilterOptions, type StatusFilter } from '../../utils/filters';
import { fullName, roleLabel } from '../../utils/format';
import { useOrganizations } from '../organizations/queries';
import { PasswordResetDialog } from '../passwordReset/PasswordResetDialog';
import { PromoteDialog } from './PromoteDialog';
import { StaffCreateDialog, StaffEditDialog } from './StaffFormDialog';
import { useStaffMutation } from './queries';
import type { StaffConfig } from './types';

type DialogState = { mode: 'create' } | { mode: 'promote' } | { mode: 'edit'; user: StaffUser } | null;

function useAssignableOrganizations(all: Organization[] | undefined): Organization[] {
  const { user, isOrgAdmin } = useAuth();
  return useMemo(
    () => (all ?? []).filter((item) => item.enabled).filter((item) => (isOrgAdmin && user ? user.organizationIds.includes(item.id) : true)),
    [all, isOrgAdmin, user],
  );
}

function matchesFilters(item: StaffUser, term: string, status: StatusFilter, organizationId: string): boolean {
  if (!matchesStatus(item.enabled, status)) return false;
  if (organizationId && !item.organizationIds.includes(Number(organizationId))) return false;
  return !term || fullName(item).toLowerCase().includes(term) || item.email.toLowerCase().includes(term) || (item.phone ?? '').includes(term);
}

export function StaffPage({ config }: { config: StaffConfig }) {
  const { t } = useI18n();
  const { isSuperAdmin } = useAuth();
  const list = config.useList();
  const organizations = useOrganizations();
  const assignable = useAssignableOrganizations(organizations.data);
  const deactivate = useStaffMutation(config.deactivate);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<StatusFilter>('');
  const [organizationFilter, setOrganizationFilter] = useState('');
  const [dialog, setDialog] = useState<DialogState>(null);
  const [deactivating, setDeactivating] = useState<StaffUser | null>(null);
  const [resetting, setResetting] = useState<StaffUser | null>(null);

  const organizationNames = useMemo(
    () => new Map((organizations.data ?? []).map((item) => [item.id, item.name])),
    [organizations.data],
  );

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (list.data ?? []).filter((item) => matchesFilters(item, term, status, organizationFilter));
  }, [list.data, search, status, organizationFilter]);

  const activeCount = useMemo(() => (list.data ?? []).filter((item) => item.enabled).length, [list.data]);

  const confirmDeactivate = () => {
    if (!deactivating) return;
    deactivate.mutate(deactivating.id, {
      onSuccess: () => {
        toast.success(t('toast.deactivated'));
        setDeactivating(null);
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    });
  };

  const columns = useMemo<ColumnDef<StaffUser>[]>(
    () => [
      {
        accessorKey: 'id',
        header: t('field.id'),
        meta: { hideBelow: 'xl', mobileHidden: true },
        cell: ({ row }) => <span className="tabular-nums text-secondary">#{row.original.id}</span>,
      },
      {
        id: 'name',
        header: t('field.fullName'),
        accessorFn: (row) => fullName(row),
        cell: ({ row }) => (
          <div className="flex min-w-0 items-center gap-3">
            <Avatar user={row.original} size="sm" />
            <span className="min-w-0">
              <span className="block truncate font-medium text-foreground">{fullName(row.original)}</span>
              <span className="block truncate text-xs text-secondary">{row.original.email}</span>
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'phone',
        header: t('field.phone'),
        meta: { hideBelow: 'xl' },
        cell: ({ row }) => <span className="tabular-nums text-secondary">{row.original.phone ?? '—'}</span>,
      },
      {
        accessorKey: 'role',
        header: t('field.role'),
        meta: { hideBelow: 'lg' },
        cell: ({ row }) => <Badge size="sm" tone="accent">{roleLabel(row.original.role, t)}</Badge>,
      },
      {
        id: 'organizations',
        header: t('field.organizations'),
        enableSorting: false,
        meta: { hideBelow: 'md' },
        cell: ({ row }) =>
          row.original.organizationIds.length === 0 ? (
            <span className="text-secondary">—</span>
          ) : (
            <div className="flex flex-wrap gap-1">
              {row.original.organizationIds.map((id) => (
                <Badge key={id} size="sm">#{id} {organizationNames.get(id) ?? ''}</Badge>
              ))}
            </div>
          ),
      },
      {
        accessorKey: 'enabled',
        header: t('field.status'),
        cell: ({ row }) => <StatusBadge enabled={row.original.enabled} />,
      },
      {
        id: 'actions',
        header: t('field.actions'),
        enableSorting: false,
        meta: { align: 'right', mobileHidden: true },
        cell: ({ row }) => (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="iconSm" aria-label={`${t('field.actions')}: ${fullName(row.original)}`}>
                <MoreHorizontalIcon aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onSelect={() => setDialog({ mode: 'edit', user: row.original })}>
                <PencilIcon />
                {t('action.edit')}
              </DropdownMenuItem>
              {isSuperAdmin && row.original.enabled && (
                <DropdownMenuItem onSelect={() => setResetting(row.original)}>
                  <KeyRoundIcon />
                  {t('users.resetPassword', 'Reset password')}
                </DropdownMenuItem>
              )}
              {row.original.enabled && (
                <DropdownMenuItem destructive onSelect={() => setDeactivating(row.original)}>
                  <PowerOffIcon />
                  {t('action.deactivate')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        ),
      },
    ],
    [t, organizationNames, isSuperAdmin],
  );

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t(config.titleKey)}
        description={config.descriptionKey ? t(config.descriptionKey) : undefined}
        badge={<Badge size="sm" tone="accent">{activeCount} {t('status.active').toLowerCase()}</Badge>}
        actions={
          <>
            <Button variant="outline" icon={<ArrowUpCircleIcon />} onClick={() => setDialog({ mode: 'promote' })}>{t('action.promote')}</Button>
            <Button icon={<PlusIcon />} onClick={() => setDialog({ mode: 'create' })}>{t(config.createKey)}</Button>
          </>
        }
      />
      <Card>
        <Toolbar summary={`${rows.length} ${t('home.resultsCount')}`}>
          <SearchInput value={search} onChange={setSearch} label={t('action.search')} className="sm:w-60" />
          <Select
            value={organizationFilter}
            aria-label={t('field.organization')}
            onValueChange={setOrganizationFilter}
            className="sm:w-48"
            options={[
              { value: '', label: `${t('field.organization')} — ${t('status.all')}` },
              ...assignable.map((item) => ({ value: String(item.id), label: item.name })),
            ]}
          />
          <Select
            value={status}
            aria-label={t('field.status')}
            onValueChange={(next) => setStatus(next as StatusFilter)}
            className="sm:w-36"
            options={statusFilterOptions(t)}
          />
        </Toolbar>
        <DataTable
          columns={columns}
          data={rows}
          rowKey={(row) => row.id}
          caption={t(config.titleKey)}
          loading={list.isPending}
          error={list.error}
          onRetry={() => void list.refetch()}
          pageSize={25}
          initialSorting={[{ id: 'name', desc: false }]}
          empty={{
            title: t('state.emptyTitle'),
            description: t('state.emptyText'),
            action: <Button size="sm" icon={<PlusIcon />} onClick={() => setDialog({ mode: 'create' })}>{t(config.createKey)}</Button>,
          }}
        />
      </Card>

      <StaffCreateDialog config={config} open={dialog?.mode === 'create'} organizations={assignable} onClose={() => setDialog(null)} />
      <StaffEditDialog
        config={config}
        open={dialog?.mode === 'edit'}
        user={dialog?.mode === 'edit' ? dialog.user : null}
        organizations={assignable}
        onClose={() => setDialog(null)}
      />
      <PromoteDialog config={config} open={dialog?.mode === 'promote'} organizations={assignable} onClose={() => setDialog(null)} />
      <PasswordResetDialog user={resetting} onClose={() => setResetting(null)} />
      <ConfirmModal
        open={Boolean(deactivating)}
        title={t('action.deactivate')}
        message={`${deactivating ? fullName(deactivating) : ''} — ${t('staff.deactivateConfirm')}`}
        confirmLabel={t('action.deactivate')}
        cancelLabel={t('action.cancel')}
        loading={deactivate.isPending}
        onConfirm={confirmDeactivate}
        onClose={() => setDeactivating(null)}
      />
    </div>
  );
}
