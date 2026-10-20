import { useCallback, useMemo, useState } from 'react';
import type { ColumnDef } from '@tanstack/react-table';
import { Building2Icon, MoreHorizontalIcon, PencilIcon, PlusIcon, PowerIcon, PowerOffIcon, ShieldCheckIcon } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';
import { SearchInput } from '../../components/ui/SearchInput';
import { Toolbar } from '../../components/ui/Toolbar';
import { DataTable } from '../../components/ui/DataTable';
import { Badge, StatusBadge, VerificationBadge } from '../../components/ui/Badge';
import { ConfirmModal } from '../../components/ui/Modal';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '../../components/ui/DropdownMenu';
import { OrganizationFormDialog } from '../../features/organizations/OrganizationFormDialog';
import { useDeactivateOrganization, useOrganizations, useReactivateOrganization, useVerifyOrganization } from '../../features/organizations/queries';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import type { Organization } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { matchesStatus, statusFilterOptions, type StatusFilter } from '../../utils/filters';
import { formatDate, truncate } from '../../utils/format';
import { matchesVerification, parseVerificationFilter, type VerificationFilter } from '../../utils/verification';

type DialogState = { mode: 'create' } | { mode: 'edit'; organization: Organization } | null;

function matchesFilters(item: Organization, term: string, status: StatusFilter, verification: VerificationFilter): boolean {
  if (!matchesStatus(item.enabled, status)) return false;
  if (!matchesVerification(item.verificationStatus, verification)) return false;
  return !term || item.name.toLowerCase().includes(term) || (item.description ?? '').toLowerCase().includes(term);
}

export function Organizations() {
  const { t, locale } = useI18n();
  const { user, hasPermission } = useAuth();
  const organizations = useOrganizations();
  const deactivate = useDeactivateOrganization();
  const reactivate = useReactivateOrganization();
  const verify = useVerifyOrganization();
  const [searchParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<StatusFilter>('');
  const [verification, setVerification] = useState<VerificationFilter>(parseVerificationFilter(searchParams.get('verification')));
  const canCreate = hasPermission('ORGANIZATIONS_CREATE');
  const canDeactivate = hasPermission('ORGANIZATIONS_DEACTIVATE');
  const canReactivate = hasPermission('ORGANIZATIONS_REACTIVATE');
  const canEdit = useCallback(
    (organization: Organization) =>
      hasPermission('ORGANIZATIONS_EDIT') || (hasPermission('ORGANIZATIONS_EDIT_OWN') && (user?.organizationIds ?? []).includes(organization.id)),
    [hasPermission, user],
  );
  const [dialog, setDialog] = useState<DialogState>(null);
  const [deactivating, setDeactivating] = useState<Organization | null>(null);

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (organizations.data ?? []).filter((item) => matchesFilters(item, term, status, verification));
  }, [organizations.data, search, status, verification]);

  const activeCount = useMemo(() => (organizations.data ?? []).filter((item) => item.enabled).length, [organizations.data]);

  const reactivateMutate = reactivate.mutate;
  const reactivateOrganization = useCallback(
    (organization: Organization) =>
      reactivateMutate(organization.id, {
        onSuccess: () => toast.success(t('toast.updated')),
        onError: (error) => toast.error(errorMessage(error, t)),
      }),
    [reactivateMutate, t],
  );

  const verifyMutate = verify.mutate;
  const verifyOrganization = useCallback(
    (organization: Organization) =>
      verifyMutate(organization.id, {
        onSuccess: () => toast.success(t('verification.done', 'Marked as verified')),
        onError: (error) => toast.error(errorMessage(error, t)),
      }),
    [verifyMutate, t],
  );

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

  const columns = useMemo<ColumnDef<Organization>[]>(() => {
    const base: ColumnDef<Organization>[] = [
      {
        accessorKey: 'id',
        header: t('field.id'),
        meta: { hideBelow: 'xl', mobileHidden: true },
        cell: ({ row }) => <span className="tabular-nums text-secondary">#{row.original.id}</span>,
      },
      {
        accessorKey: 'name',
        header: t('field.name'),
        cell: ({ row }) => (
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-pill bg-surface text-foreground" aria-hidden="true">
              <Building2Icon className="h-4 w-4" />
            </span>
            <span className="min-w-0">
              <span className="block truncate font-medium text-foreground">{row.original.name}</span>
              <span className="block truncate text-xs text-secondary md:hidden">{truncate(row.original.description, 60)}</span>
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'description',
        header: t('field.description'),
        enableSorting: false,
        meta: { hideBelow: 'md', mobileHidden: true },
        cell: ({ row }) => <span className="text-secondary">{truncate(row.original.description, 90)}</span>,
      },
      {
        accessorKey: 'enabled',
        header: t('field.status'),
        cell: ({ row }) => <StatusBadge enabled={row.original.enabled} />,
      },
      {
        id: 'verification',
        header: t('verification.column', 'Verification'),
        enableSorting: false,
        meta: { hideBelow: 'md' },
        cell: ({ row }) => <VerificationBadge status={row.original.verificationStatus} />,
      },
      {
        accessorKey: 'createdAt',
        header: t('field.createdAt'),
        meta: { hideBelow: 'lg' },
        cell: ({ row }) => <span className="tabular-nums text-secondary">{formatDate(row.original.createdAt, locale)}</span>,
      },
    ];
    const hasRowActions = (organization: Organization) =>
      canEdit(organization) || (organization.enabled ? canDeactivate : canReactivate);
    if (!(organizations.data ?? []).some(hasRowActions)) return base;
    return [
      ...base,
      {
        id: 'actions',
        header: t('field.actions'),
        enableSorting: false,
        meta: { align: 'right', mobileHidden: true },
        cell: ({ row }) => !hasRowActions(row.original) ? null : (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="iconSm" aria-label={`${t('field.actions')}: ${row.original.name}`}>
                <MoreHorizontalIcon aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              {canEdit(row.original) && (
                <DropdownMenuItem onSelect={() => setDialog({ mode: 'edit', organization: row.original })}>
                  <PencilIcon />
                  {t('action.edit')}
                </DropdownMenuItem>
              )}
              {canEdit(row.original) && (
                <DropdownMenuItem onSelect={() => verifyOrganization(row.original)}>
                  <ShieldCheckIcon />
                  {t('verification.verify', 'Mark as verified by organization')}
                </DropdownMenuItem>
              )}
              {row.original.enabled && canDeactivate && (
                <DropdownMenuItem destructive onSelect={() => setDeactivating(row.original)}>
                  <PowerOffIcon />
                  {t('action.deactivate')}
                </DropdownMenuItem>
              )}
              {!row.original.enabled && canReactivate && (
                <DropdownMenuItem onSelect={() => reactivateOrganization(row.original)}>
                  <PowerIcon />
                  {t('action.reactivate', 'Reactivate')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        ),
      },
    ];
  }, [t, locale, canEdit, canDeactivate, canReactivate, organizations.data, reactivateOrganization, verifyOrganization]);

  const createButton = (size: 'sm' | 'md') =>
    canCreate ? (
      <Button size={size} icon={<PlusIcon />} onClick={() => setDialog({ mode: 'create' })}>{t('orgs.create')}</Button>
    ) : undefined;

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('orgs.title')}
        description={t('home.orgsSubtitle')}
        badge={<Badge size="sm" tone="accent">{activeCount} {t('status.active').toLowerCase()}</Badge>}
        actions={createButton('md')}
      />
      <Card>
        <Toolbar summary={`${rows.length} ${t('home.resultsCount')}`}>
          <SearchInput value={search} onChange={setSearch} label={t('action.search')} className="sm:w-64" />
          <Select
            value={status}
            aria-label={t('field.status')}
            onValueChange={(next) => setStatus(next as StatusFilter)}
            className="sm:w-40"
            options={statusFilterOptions(t)}
          />
          <Select
            value={verification}
            aria-label={t('verification.column', 'Verification')}
            onValueChange={(next) => setVerification(parseVerificationFilter(next))}
            className="sm:w-48"
            options={[
              { value: '', label: `${t('verification.column', 'Verification')} · ${t('status.all')}` },
              { value: 'due', label: t('verification.filterDue', 'Needs recheck') },
              { value: 'verified', label: t('verification.status.VERIFIED', 'Verified') },
            ]}
          />
        </Toolbar>
        <DataTable
          columns={columns}
          data={rows}
          rowKey={(row) => row.id}
          caption={t('orgs.title')}
          loading={organizations.isPending}
          error={organizations.error}
          onRetry={() => void organizations.refetch()}
          pageSize={25}
          initialSorting={[{ id: 'createdAt', desc: true }]}
          empty={{ title: t('state.emptyTitle'), description: t('state.emptyText'), action: createButton('sm') }}
        />
      </Card>

      <OrganizationFormDialog
        open={dialog !== null}
        organization={dialog?.mode === 'edit' ? dialog.organization : null}
        onClose={() => setDialog(null)}
      />
      <ConfirmModal
        open={Boolean(deactivating)}
        title={t('action.deactivate')}
        message={`${deactivating?.name ?? ''} — ${t('orgs.deactivateConfirm')}`}
        confirmLabel={t('action.deactivate')}
        cancelLabel={t('action.cancel')}
        loading={deactivate.isPending}
        onConfirm={confirmDeactivate}
        onClose={() => setDeactivating(null)}
      />
    </div>
  );
}
