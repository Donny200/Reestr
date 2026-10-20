import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import type { ColumnDef } from '@tanstack/react-table';
import { BellIcon, BuildingIcon, FileTextIcon, FlagIcon, PlusIcon, ShieldAlertIcon, ShieldCheckIcon, UsersIcon } from 'lucide-react';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Badge, FunctionStatusBadge, StatusBadge } from '../../components/ui/Badge';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { DataTable } from '../../components/ui/DataTable';
import { StatCard } from '../../components/ui/StatCard';
import { Skeleton } from '../../components/ui/Skeleton';
import { useOrganizations } from '../../features/organizations/queries';
import { useOrganizationNames } from '../../features/organizations/useOrganizationNames';
import { useAdminFunctions } from '../../features/functions/queries';
import { useReminders } from '../../features/analytics/queries';
import { useReports } from '../../features/reports/queries';
import { canViewReports } from '../../features/reports/reportOptions';
import { needsRecheck } from '../../utils/verification';
import { useModerators, useOrgAdmins } from '../../features/staff/queries';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { functionText } from '../../utils/functionLocalization';
import { useCategoryNames } from '../../features/functions/useCategoryNames';
import { formatDate, fullName, roleLabel, truncate } from '../../utils/format';
import { FUNCTION_STATUSES, type AdminFunction } from '../../types/adminFunctions';
import type { Organization } from '../../types/api';

export function Dashboard() {
  const { t, locale } = useI18n();
  const { user, isSuperAdmin, hasRole, hasPermission } = useAuth();
  const canSeeOrgAdmins = isSuperAdmin;
  const canSeeModerators = hasRole('ROLE_SUPER_ADMIN', 'ROLE_ORG_ADMIN');
  const canSeeFunctions = hasPermission('FUNCTIONS_VIEW');
  const canSeeReports = canViewReports(hasPermission);
  const canSeeAnalytics = hasPermission('ORG_ANALYTICS_VIEW');

  const organizations = useOrganizations();
  const functions = useAdminFunctions(canSeeFunctions);
  const orgAdmins = useOrgAdmins(canSeeOrgAdmins);
  const moderators = useModerators(canSeeModerators);
  const openReports = useReports({ status: 'OPEN' }, canSeeReports);
  const reminders = useReminders({}, canSeeAnalytics);

  const orgList = useMemo(() => organizations.data ?? [], [organizations.data]);
  const fnList = useMemo(() => functions.data ?? [], [functions.data]);
  const categoryName = useCategoryNames();
  const organizationNames = useOrganizationNames(organizations.data);
  const organizationName = useCallback((id: number | null) => (id == null ? undefined : organizationNames.get(id)) ?? '—', [organizationNames]);

  const cards = [
    {
      key: 'organizations',
      label: t('admin.summaryOrgs'),
      value: orgList.length,
      hint: t('admin.activeCount', { count: orgList.filter((item) => item.enabled).length }),
      icon: BuildingIcon,
      to: '/admin/organizations',
      loading: organizations.isPending,
      visible: true,
    },
    {
      key: 'org-admins',
      label: t('admin.summaryOrgAdmins'),
      value: (orgAdmins.data ?? []).length,
      hint: t('admin.activeCount', { count: (orgAdmins.data ?? []).filter((item) => item.enabled).length }),
      icon: ShieldCheckIcon,
      to: '/admin/org-admins',
      loading: orgAdmins.isPending,
      visible: canSeeOrgAdmins,
    },
    {
      key: 'moderators',
      label: t('admin.summaryModerators'),
      value: (moderators.data ?? []).length,
      hint: t('admin.activeCount', { count: (moderators.data ?? []).filter((item) => item.enabled).length }),
      icon: UsersIcon,
      to: '/admin/moderators',
      loading: moderators.isPending,
      visible: canSeeModerators,
    },
    {
      key: 'functions',
      label: t('admin.summaryFunctions'),
      value: fnList.length,
      hint: t('admin.publishedCount', { count: fnList.filter((item) => item.status === 'PUBLISHED').length }),
      icon: FileTextIcon,
      to: '/admin/functions',
      loading: functions.isPending,
      visible: canSeeFunctions,
    },
    {
      key: 'functions-recheck',
      label: t('verification.dashboardServices', 'Services to verify'),
      value: fnList.filter((item) => item.status === 'PUBLISHED' && needsRecheck(item.verificationStatus)).length,
      hint: t('verification.dashboardServicesHint', 'Published, need a check'),
      icon: ShieldAlertIcon,
      to: '/admin/functions?status=PUBLISHED&verification=due',
      loading: functions.isPending,
      visible: canSeeFunctions,
    },
    {
      key: 'organizations-recheck',
      label: t('verification.dashboardOrganizations', 'Organizations to verify'),
      value: orgList.filter((item) => item.enabled && needsRecheck(item.verificationStatus)).length,
      hint: t('verification.dashboardOrganizationsHint', 'Active, need a check'),
      icon: ShieldAlertIcon,
      to: '/admin/organizations?verification=due',
      loading: organizations.isPending,
      visible: true,
    },
    {
      key: 'reports',
      label: t('reports.dashboardTitle', 'Open reports'),
      value: (openReports.data ?? []).length,
      hint: t('reports.newCount', { count: (openReports.data ?? []).filter((item) => item.status === 'NEW').length }),
      icon: FlagIcon,
      to: '/admin/reports',
      loading: openReports.isPending,
      visible: canSeeReports,
    },
    {
      key: 'reminders',
      label: t('reminders.title', 'Reminders'),
      value: reminders.data?.total ?? 0,
      hint: t('reminders.dashboardHint', 'Quality issues to follow up'),
      icon: BellIcon,
      to: '/admin/organization-dashboard#reminders',
      loading: reminders.isPending,
      visible: canSeeAnalytics,
    },
  ].filter((card) => card.visible);

  const recentOrganizations = useMemo(
    () => [...orgList].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5),
    [orgList],
  );
  const recentFunctions = useMemo(() => [...fnList].sort((a, b) => b.id - a.id).slice(0, 8), [fnList]);
  const breakdown = FUNCTION_STATUSES.map((status) => {
    const count = fnList.filter((item) => item.status === status).length;
    return { status, count, share: fnList.length === 0 ? 0 : Math.round((count / fnList.length) * 100) };
  });

  const organizationColumns = useMemo<ColumnDef<Organization>[]>(
    () => [
      {
        accessorKey: 'name',
        header: t('field.name'),
        cell: ({ row }) => (
          <Link to={`/organizations/${row.original.id}`} className="rounded-sm font-medium text-foreground underline-offset-4 wrap-anywhere fine:hover:underline">
            {organizationName(row.original.id)}
          </Link>
        ),
      },
      { accessorKey: 'enabled', header: t('field.status'), enableSorting: false, cell: ({ row }) => <StatusBadge enabled={row.original.enabled} /> },
      {
        accessorKey: 'createdAt',
        header: t('field.createdAt'),
        meta: { hideBelow: 'md' },
        cell: ({ row }) => <span className="tabular-nums text-secondary">{formatDate(row.original.createdAt, locale)}</span>,
      },
    ],
    [t, locale, organizationName],
  );

  const functionColumns = useMemo<ColumnDef<AdminFunction>[]>(
    () => [
      { accessorKey: 'status', header: t('field.status'), enableSorting: false, cell: ({ row }) => <FunctionStatusBadge status={row.original.status} /> },
      {
        id: 'name',
        header: t('field.name'),
        accessorFn: (row) => functionText(row, 'name', locale),
        cell: ({ row }) => (
          <div className="min-w-0 max-w-md">
            <Link to={`/admin/functions/${row.original.id}`} className="rounded-sm font-medium text-foreground underline-offset-4 wrap-anywhere fine:hover:underline">
              {functionText(row.original, 'name', locale)}
            </Link>
            <p className="mt-0.5 text-xs text-secondary wrap-anywhere">{truncate(functionText(row.original, 'description', locale), 80)}</p>
          </div>
        ),
      },
      {
        accessorKey: 'category',
        header: t('field.category'),
        enableSorting: false,
        meta: { hideBelow: 'lg' },
        cell: ({ row }) => (row.original.category ? <Badge size="sm" tone="accent">{categoryName(row.original.categoryId, row.original.category)}</Badge> : <span className="text-secondary">—</span>),
      },
      {
        id: 'organization',
        header: t('field.organization'),
        enableSorting: false,
        meta: { hideBelow: 'xl' },
        cell: ({ row }) => <span className="text-secondary">{organizationName(row.original.organizationId)}</span>,
      },
    ],
    [t, locale, organizationName, categoryName],
  );

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.dashboard')}
        title={`${t('admin.welcome')}${user ? `, ${fullName(user)}` : ''}`}
        description={user ? `${roleLabel(user.role, t)} · ${user.email}` : undefined}
        actions={
          hasPermission('FUNCTIONS_CREATE') ? (
            <Link to="/admin/functions/new" className={buttonVariants({ variant: 'dark', size: 'sm' })}>
              <PlusIcon aria-hidden="true" />
              {t('fnAdmin.create')}
            </Link>
          ) : undefined
        }
      />

      <div className="grid gap-4 min-[420px]:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <StatCard key={card.key} label={card.label} value={card.value} hint={card.hint} icon={card.icon} to={card.to} loading={card.loading} />
        ))}
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)] xl:items-start">
        <Card>
          <CardHeader
            title={t('admin.recentOrgs')}
            description={t('home.orgsSubtitle')}
            actions={<Link to="/admin/organizations" className={buttonVariants({ variant: 'outline', size: 'sm' })}>{t('action.viewAll')}</Link>}
          />
          <DataTable
            columns={organizationColumns}
            data={recentOrganizations}
            rowKey={(row) => row.id}
            caption={t('admin.recentOrgs')}
            pageSize={8}
            loading={organizations.isPending}
            error={organizations.error}
            onRetry={() => void organizations.refetch()}
            empty={{ title: t('state.emptyTitle') }}
          />
        </Card>

        {canSeeFunctions && (
          <Card>
            <CardHeader title={t('admin.statusBreakdown', 'Catalogue status')} />
            <CardBody className="space-y-5">
              {functions.isPending ? (
                <div className="space-y-5">
                  {FUNCTION_STATUSES.map((status) => (
                    <div key={status} className="space-y-2">
                      <Skeleton className="h-5 w-32 rounded-pill" />
                      <Skeleton className="h-1.5 w-full rounded-pill" />
                    </div>
                  ))}
                </div>
              ) : (
                breakdown.map((item) => (
                  <div key={item.status}>
                    <div className="flex items-center justify-between gap-3">
                      <FunctionStatusBadge status={item.status} />
                      <span className="text-sm tabular-nums text-secondary">
                        <span className="font-semibold text-foreground">{item.count}</span> · {item.share}%
                      </span>
                    </div>
                    <div className="mt-2 h-1.5 overflow-hidden rounded-pill bg-surface-2" aria-hidden="true">
                      <div className="h-full rounded-pill bg-ink transition-[width] duration-reveal ease-out" style={{ width: `${item.share}%` }} />
                    </div>
                  </div>
                ))
              )}
            </CardBody>
          </Card>
        )}
      </div>

      {canSeeFunctions && (
        <Card className="mt-6">
          <CardHeader
            title={t('admin.recentFunctions')}
            description={t('home.catalogSubtitle')}
            actions={<Link to="/admin/functions" className={buttonVariants({ variant: 'outline', size: 'sm' })}>{t('action.viewAll')}</Link>}
          />
          <DataTable
            columns={functionColumns}
            data={recentFunctions}
            rowKey={(row) => row.id}
            caption={t('admin.recentFunctions')}
            pageSize={8}
            loading={functions.isPending}
            error={functions.error}
            onRetry={() => void functions.refetch()}
            empty={{ title: t('state.emptyTitle') }}
          />
        </Card>
      )}
    </div>
  );
}
