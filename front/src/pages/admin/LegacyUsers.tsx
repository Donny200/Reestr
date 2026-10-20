import { useMemo, useState } from 'react';
import type { ColumnDef } from '@tanstack/react-table';
import { KeyRoundIcon, MonitorSmartphoneIcon, MoreHorizontalIcon, PencilIcon, PlusIcon, PowerOffIcon, ShieldOffIcon } from 'lucide-react';
import { toast } from 'sonner';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';
import { SearchInput } from '../../components/ui/SearchInput';
import { Toolbar } from '../../components/ui/Toolbar';
import { DataTable } from '../../components/ui/DataTable';
import { Avatar } from '../../components/ui/Avatar';
import { Badge, StatusBadge, StatusPill } from '../../components/ui/Badge';
import { ConfirmModal } from '../../components/ui/Modal';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '../../components/ui/DropdownMenu';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { LegacyUserCreateDialog, LegacyUserEditDialog } from '../../features/legacyUsers/LegacyUserDialogs';
import { useDeactivateLegacyUser, useLegacyUsers } from '../../features/legacyUsers/queries';
import { useOrganizations } from '../../features/organizations/queries';
import { PasswordResetDialog } from '../../features/passwordReset/PasswordResetDialog';
import { UserSessionsDialog } from '../../features/sessions/UserSessionsDialog';
import { useResetUserMfa } from '../../features/legacyUsers/queries';
import { useAuth } from '../../contexts/auth';
import { useRoles } from '../../features/roles/queries';
import { useI18n } from '../../contexts/i18n';
import type { LegacyUser } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { matchesStatus, statusFilterOptions, type StatusFilter } from '../../utils/filters';
import { fullName, roleLabel } from '../../utils/format';

type DialogState = { mode: 'create' } | { mode: 'edit'; user: LegacyUser } | null;

function matchesFilters(item: LegacyUser, term: string, status: StatusFilter, role: string): boolean {
  if (!matchesStatus(item.enabled, status)) return false;
  if (role && item.role !== role) return false;
  return !term || fullName(item).toLowerCase().includes(term) || item.email.toLowerCase().includes(term);
}

export function LegacyUsers() {
  const { t } = useI18n();
  const users = useLegacyUsers();
  const organizations = useOrganizations();
  const roles = useRoles();
  const deactivate = useDeactivateLegacyUser();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<StatusFilter>('');
  const [role, setRole] = useState('');
  const [dialog, setDialog] = useState<DialogState>(null);
  const [deactivating, setDeactivating] = useState<LegacyUser | null>(null);
  const [resetting, setResetting] = useState<LegacyUser | null>(null);
  const [resettingMfa, setResettingMfa] = useState<LegacyUser | null>(null);
  const [sessionsUser, setSessionsUser] = useState<LegacyUser | null>(null);
  const resetMfa = useResetUserMfa();
  const { isSuperAdmin } = useAuth();

  const organizationNames = useMemo(
    () => new Map((organizations.data ?? []).map((item) => [item.id, item.name])),
    [organizations.data],
  );

  const roleOptions = useMemo(() => Array.from(new Set((users.data ?? []).map((item) => item.role))).sort(), [users.data]);

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (users.data ?? []).filter((item) => matchesFilters(item, term, status, role));
  }, [users.data, search, status, role]);

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

  const columns = useMemo<ColumnDef<LegacyUser>[]>(
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
        meta: { hideBelow: 'md' },
        cell: ({ row }) => <Badge size="sm" tone="accent">{roleLabel(row.original.role, t)}</Badge>,
      },
      {
        id: 'organizations',
        header: t('field.organizations'),
        enableSorting: false,
        meta: { hideBelow: 'lg' },
        cell: ({ row }) =>
          row.original.organizations.length === 0 ? (
            <span className="text-secondary">—</span>
          ) : (
            <div className="flex flex-wrap gap-1">
              {row.original.organizations.map((id) => (
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
        id: 'mfa',
        header: t('mfa.column', '2FA'),
        enableSorting: false,
        meta: { hideBelow: 'lg' },
        cell: ({ row }) =>
          row.original.mfa === null ? (
            <span className="text-secondary">—</span>
          ) : row.original.mfa.enrolled ? (
            <StatusPill tone="published">{t('mfa.stateEnabled', 'Enabled')}</StatusPill>
          ) : row.original.mfa.required ? (
            <StatusPill tone={row.original.mfa.enrollmentOverdue ? 'danger' : 'pending'}>{t('mfa.stateNotEnrolled', 'Not enrolled')}</StatusPill>
          ) : (
            <StatusPill tone="draft">{t('mfa.stateOptional', 'Optional')}</StatusPill>
          ),
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
              {isSuperAdmin && (
                <DropdownMenuItem onSelect={() => setSessionsUser(row.original)}>
                  <MonitorSmartphoneIcon />
                  {t('sessions.title', 'Active sessions')}
                </DropdownMenuItem>
              )}
              {isSuperAdmin && row.original.mfa?.enrolled && (
                <DropdownMenuItem destructive onSelect={() => setResettingMfa(row.original)}>
                  <ShieldOffIcon />
                  {t('mfa.adminReset', 'Reset two-factor authentication')}
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
        title={t('legacy.title')}
        description={t('legacy.notice')}
        badge={<StatusPill tone="pending">{t('legacy.badge')}</StatusPill>}
        actions={<Button icon={<PlusIcon />} onClick={() => setDialog({ mode: 'create' })}>{t('action.create')}</Button>}
      />

      <InlineAlert tone="warning" className="mb-6">
        {t('legacy.notice')} <span className="font-semibold">{t('legacy.updateNotice')}</span>
      </InlineAlert>

      <Card>
        <Toolbar summary={`${rows.length} ${t('home.resultsCount')}`}>
          <SearchInput value={search} onChange={setSearch} label={t('action.search')} className="sm:w-64" />
          <Select
            value={role}
            aria-label={t('field.role')}
            onValueChange={setRole}
            className="sm:w-48"
            options={[
              { value: '', label: `${t('field.role')} — ${t('status.all')}` },
              ...roleOptions.map((item) => ({ value: item, label: roleLabel(item, t) })),
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
          caption={t('legacy.title')}
          loading={users.isPending}
          error={users.error}
          onRetry={() => void users.refetch()}
          pageSize={25}
          initialSorting={[{ id: 'name', desc: false }]}
          empty={{ title: t('state.emptyTitle'), description: t('state.emptyText') }}
        />
      </Card>

      <LegacyUserCreateDialog
        open={dialog?.mode === 'create'}
        roles={roles.data ?? []}
        organizations={organizations.data ?? []}
        onClose={() => setDialog(null)}
      />
      <LegacyUserEditDialog open={dialog?.mode === 'edit'} user={dialog?.mode === 'edit' ? dialog.user : null} onClose={() => setDialog(null)} />
      <PasswordResetDialog user={resetting} onClose={() => setResetting(null)} />
      <UserSessionsDialog user={sessionsUser} onClose={() => setSessionsUser(null)} />
      <ConfirmModal
        open={Boolean(resettingMfa)}
        title={t('mfa.adminReset', 'Reset two-factor authentication')}
        message={`${resettingMfa ? fullName(resettingMfa) : ''} — ${t('mfa.adminResetConfirm', 'Remove this user’s second factor and end all their sessions? They will set it up again at their next sign-in. You will never see their secret or codes.')}`}
        confirmLabel={t('mfa.adminReset', 'Reset two-factor authentication')}
        cancelLabel={t('action.cancel')}
        loading={resetMfa.isPending}
        onConfirm={() => resettingMfa && resetMfa.mutate(resettingMfa.id, {
          onSuccess: () => { toast.success(t('mfa.adminResetDone', 'Two-factor authentication reset')); setResettingMfa(null); },
          onError: (error) => toast.error(errorMessage(error, t)),
        })}
        onClose={() => setResettingMfa(null)}
      />
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
