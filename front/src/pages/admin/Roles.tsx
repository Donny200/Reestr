import { useMemo, useState } from 'react';
import type { ColumnDef } from '@tanstack/react-table';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { KeyRoundIcon, MoreHorizontalIcon, PlusIcon, ShieldCheckIcon, ShieldIcon, Trash2Icon, UserCheckIcon } from 'lucide-react';
import { toast } from 'sonner';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Select } from '../../components/ui/Select';
import { SearchInput } from '../../components/ui/SearchInput';
import { Toolbar } from '../../components/ui/Toolbar';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { ConfirmModal } from '../../components/ui/Modal';
import { SkeletonText } from '../../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '../../components/ui/DropdownMenu';
import { CreateRoleDialog, RolePermissionsDialog } from '../../features/roles/RoleDialogs';
import { groupPermissions } from '../../features/roles/permissions';
import { useAssignRole, useDeleteRole, usePermissions, useRoleAssignmentCandidates, useRoles } from '../../features/roles/queries';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors } from '../../lib/forms';
import type { RoleEntity } from '../../types/api';
import { errorMessage, type Translate } from '../../utils/errors';
import { fullName } from '../../utils/format';

const SYSTEM_ROLES = new Set(['ROLE_SUPER_ADMIN', 'ROLE_ORG_ADMIN', 'ROLE_MODERATOR']);

const assignSchema = (t: Translate) =>
  z.object({
    userId: z.string().min(1, t('validation.required')),
    roleId: z.string().min(1, t('validation.required')),
  });
type AssignFormValues = z.infer<ReturnType<typeof assignSchema>>;

function AssignRoleCard({ roles }: { roles: RoleEntity[] }) {
  const { t } = useI18n();
  const candidates = useRoleAssignmentCandidates();
  const assign = useAssignRole();
  const schema = useMemo(() => assignSchema(t), [t]);
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AssignFormValues>({ resolver: zodResolver(schema), defaultValues: { userId: '', roleId: '' } });

  const submit = handleSubmit(async (values) => {
    try {
      await assign.mutateAsync({ userId: Number(values.userId), roleId: Number(values.roleId) });
      toast.success(t('toast.updated'));
      reset({ userId: '', roleId: '' });
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t);
    }
  });

  return (
    <Card>
      <CardHeader title={t('roles.assignTitle')} icon={<UserCheckIcon className="h-4 w-4" aria-hidden="true" />} />
      <CardBody>
        <form onSubmit={submit} noValidate className="grid gap-5 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] sm:items-end">
          <Field label={t('staff.selectUser')} error={errors.userId?.message} required>
            {(fieldControl) => (
              <Controller
                control={control}
                name="userId"
                render={({ field }) => (
                  <Select
                    {...fieldControl}
                    ref={field.ref}
                    disabled={candidates.isPending}
                    value={field.value}
                    onValueChange={field.onChange}
                    onBlur={field.onBlur}
                    placeholder={t('staff.selectUser')}
                    options={(candidates.data ?? []).map((candidate) => ({
                      value: String(candidate.id),
                      label: `#${candidate.id} — ${fullName(candidate)}`,
                      description: candidate.email,
                    }))}
                  />
                )}
              />
            )}
          </Field>
          <Field label={t('field.role')} error={errors.roleId?.message} required>
            {(fieldControl) => (
              <Controller
                control={control}
                name="roleId"
                render={({ field }) => (
                  <Select
                    {...fieldControl}
                    ref={field.ref}
                    value={field.value}
                    onValueChange={field.onChange}
                    onBlur={field.onBlur}
                    placeholder={t('field.role')}
                    options={roles.map((role) => ({ value: String(role.id), label: role.name }))}
                  />
                )}
              />
            )}
          </Field>
          <Button type="submit" loading={isSubmitting} icon={<UserCheckIcon />} className="sm:mb-0">{t('action.assign')}</Button>
        </form>
      </CardBody>
    </Card>
  );
}

export function Roles() {
  const { t } = useI18n();
  const roles = useRoles();
  const permissions = usePermissions();
  const remove = useDeleteRole();
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<RoleEntity | null>(null);
  const [deleting, setDeleting] = useState<RoleEntity | null>(null);

  const groups = useMemo(() => groupPermissions(permissions.data ?? [], t('roles.uncategorized', 'Other')), [permissions.data, t]);

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    return (roles.data ?? []).filter((role) => !term || role.name.toLowerCase().includes(term));
  }, [roles.data, search]);

  const confirmDelete = () => {
    if (!deleting) return;
    remove.mutate(deleting.id, {
      onSuccess: () => {
        toast.success(t('toast.deleted'));
        setDeleting(null);
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    });
  };

  const columns = useMemo<ColumnDef<RoleEntity>[]>(
    () => [
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
              {SYSTEM_ROLES.has(row.original.name) ? <ShieldCheckIcon className="h-4 w-4" /> : <ShieldIcon className="h-4 w-4" />}
            </span>
            <span className="min-w-0">
              <span className="block truncate font-medium text-foreground">{row.original.name}</span>
              <span className="block text-xs text-secondary">
                {SYSTEM_ROLES.has(row.original.name) ? t('roles.system', 'System role') : t('roles.custom', 'Custom role')}
              </span>
            </span>
          </div>
        ),
      },
      {
        id: 'permissions',
        header: t('roles.permissions'),
        accessorFn: (row) => row.permissions.length,
        cell: ({ row }) =>
          row.original.permissions.length === 0 ? (
            <span className="text-secondary">—</span>
          ) : (
            <div className="flex flex-wrap gap-1">
              {row.original.permissions.slice(0, 4).map((permission) => (
                <Badge key={permission.id} size="sm">{permission.code}</Badge>
              ))}
              {row.original.permissions.length > 4 && <Badge size="sm" tone="accent">+{row.original.permissions.length - 4}</Badge>}
            </div>
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
              <Button variant="ghost" size="iconSm" aria-label={`${t('field.actions')}: ${row.original.name}`}>
                <MoreHorizontalIcon aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onSelect={() => setEditing(row.original)}>
                <KeyRoundIcon />
                {t('roles.editPermissions')}
              </DropdownMenuItem>
              {!SYSTEM_ROLES.has(row.original.name) && (
                <DropdownMenuItem destructive onSelect={() => setDeleting(row.original)}>
                  <Trash2Icon />
                  {t('action.delete')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        ),
      },
    ],
    [t],
  );

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('roles.title')}
        badge={<Badge size="sm" tone="accent">{(roles.data ?? []).length}</Badge>}
        actions={<Button icon={<PlusIcon />} onClick={() => setCreateOpen(true)}>{t('roles.createRole')}</Button>}
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.8fr)_minmax(18rem,1fr)]">
        <div className="space-y-6">
          <Card>
            <Toolbar summary={`${rows.length} ${t('home.resultsCount')}`}>
              <SearchInput value={search} onChange={setSearch} label={t('action.search')} className="sm:w-64" />
            </Toolbar>
            <DataTable
              columns={columns}
              data={rows}
              rowKey={(row) => row.id}
              caption={t('roles.title')}
              loading={roles.isPending}
              error={roles.error}
              onRetry={() => void roles.refetch()}
              pageSize={25}
              initialSorting={[{ id: 'name', desc: false }]}
              empty={{ title: t('state.emptyTitle') }}
            />
          </Card>
          <AssignRoleCard roles={roles.data ?? []} />
        </div>

        <Card className="h-fit xl:sticky xl:top-24">
          <CardHeader
            title={t('roles.permissionsPanel')}
            description={`${(permissions.data ?? []).length}`}
            icon={<KeyRoundIcon className="h-4 w-4" aria-hidden="true" />}
          />
          <CardBody className="max-h-[560px] overflow-y-auto">
            {permissions.isPending ? (
              <SkeletonText lines={8} />
            ) : permissions.error ? (
              <ErrorState error={permissions.error} onRetry={() => void permissions.refetch()} />
            ) : groups.length === 0 ? (
              <EmptyState title={t('state.emptyTitle')} />
            ) : (
              <div className="space-y-5">
                {groups.map(([category, items]) => (
                  <div key={category}>
                    <p className="text-xs font-semibold uppercase tracking-wide text-secondary">{category}</p>
                    <ul className="mt-2 space-y-1.5">
                      {items.map((permission) => (
                        <li key={permission.id} className="rounded-control border border-line bg-background px-3 py-2">
                          <p className="text-sm font-medium text-foreground">{permission.name}</p>
                          <p className="text-xs text-secondary">{permission.code}</p>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      <CreateRoleDialog open={createOpen} onClose={() => setCreateOpen(false)} />
      <RolePermissionsDialog role={editing} groups={groups} onClose={() => setEditing(null)} />
      <ConfirmModal
        open={Boolean(deleting)}
        title={t('action.delete')}
        message={`${deleting?.name ?? ''} — ${t('roles.deleteConfirm')}`}
        confirmLabel={t('action.delete')}
        cancelLabel={t('action.cancel')}
        loading={remove.isPending}
        onConfirm={confirmDelete}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
