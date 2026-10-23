import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import type { ColumnDef } from '@tanstack/react-table';
import { GitBranchIcon, LanguagesIcon, PlusIcon, TriangleAlertIcon } from 'lucide-react';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card, CardBody } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Badge, FunctionStatusBadge, StatusPill, VerificationBadge } from '../../components/ui/Badge';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { Tooltip } from '../../components/ui/Tooltip';
import { Toolbar } from '../../components/ui/Toolbar';
import { FunctionErrorState } from '../../features/functions/FunctionErrorState';
import { TranslationBackfillButton } from '../../features/functions/TranslationBackfillButton';
import { useAdminFunctions, useFunctionOptions } from '../../features/functions/queries';
import { useI18n } from '../../contexts/i18n';
import { useAuth } from '../../contexts/auth';
import { ApiError } from '../../services/http';
import { functionText, hasFunctionTranslation } from '../../utils/functionLocalization';
import { localizedText } from '../../utils/translations';
import { FUNCTION_STATUSES, isEditableStatus, functionStatusLabel, type AdminFunction, type FunctionStatus } from '../../types/adminFunctions';
import { matchesVerification, parseVerificationFilter, type VerificationFilter } from '../../utils/verification';

const PAGE_SIZE = 50;

export function AdminFunctionsPage() {
  const { t, locale, available } = useI18n();
  const { hasPermission, user } = useAuth();
  const canView = hasPermission('FUNCTIONS_VIEW');
  const functions = useAdminFunctions(canView);
  const options = useFunctionOptions();
  const [searchParams] = useSearchParams();
  const initialStatus = searchParams.get('status');
  const [status, setStatus] = useState(FUNCTION_STATUSES.includes(initialStatus as FunctionStatus) ? (initialStatus as string) : '');
  const [verification, setVerification] = useState<VerificationFilter>(parseVerificationFilter(searchParams.get('verification')));
  const [organization, setOrganization] = useState('');
  const [assignee, setAssignee] = useState(searchParams.get('assignee') === 'me' ? 'me' : searchParams.get('assignee') === 'unassigned' ? 'unassigned' : '');
  const [category, setCategory] = useState('');
  const [language, setLanguage] = useState(locale);
  const [search, setSearch] = useState('');
  const forbidden = useMemo(() => new ApiError(403, t('state.forbidden')), [t]);

  useEffect(() => setLanguage(locale), [locale]);

  const organizationName = useCallback(
    (id: number | null) =>
      options.data?.organizations.find((item) => item.id === id)?.name ?? (id === null ? t('fnAdmin.unassigned') : `#${id}`),
    [options.data, t],
  );

  const rows = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    return (functions.data ?? [])
      .filter(
        (row) =>
          (!status || row.status === status) &&
          (!organization || (organization === 'unassigned' ? row.organizationId === null : row.organizationId === Number(organization))) &&
          (!category || row.categoryId === Number(category)) &&
          (!assignee || (assignee === 'unassigned' ? !row.assignee && !row.revisionOfId : row.assignee?.userId === user?.id)) &&
          matchesVerification(row.verificationStatus, verification) &&
          functionText(row, 'name', language).toLocaleLowerCase().includes(term),
      )
      .sort((a, b) => b.id - a.id);
  }, [functions.data, status, organization, category, verification, language, search, assignee, user?.id]);

  const columns = useMemo<ColumnDef<AdminFunction>[]>(
    () => [
      {
        id: 'name',
        header: t('field.name'),
        accessorFn: (row) => functionText(row, 'name', language),
        cell: ({ row }) => (
          <div className="min-w-0 max-w-md" lang={language}>
            <Link to={`/admin/functions/${row.original.id}`} className="rounded-sm font-medium text-foreground underline-offset-4 wrap-anywhere fine:hover:underline">
              {functionText(row.original, 'name', language)}
            </Link>
            <p className="mt-0.5 line-clamp-2 text-xs leading-5 text-secondary wrap-anywhere">{functionText(row.original, 'description', language)}</p>
            {row.original.revisionOfId && (
              <p className="mt-1 inline-flex items-center gap-1 text-xs text-secondary" lang={locale}>
                <GitBranchIcon className="h-3 w-3" aria-hidden="true" />
                {t('fnAdmin.revisionOf', 'Draft revision of service')} #{row.original.revisionOfId}
              </p>
            )}
            {row.original.assignee && (
              <p className="mt-1 text-xs text-secondary wrap-anywhere" lang={locale}>
                {t('assignee.short', 'Responsible')}: {row.original.assignee.userId === user?.id ? t('verification.byYou', 'you') : row.original.assignee.name ?? row.original.assignee.email}
              </p>
            )}
            {!hasFunctionTranslation(row.original, language) && (
              <p className="mt-1 inline-flex items-center gap-1 text-xs text-status-pending" lang={locale}>
                <TriangleAlertIcon className="h-3 w-3" aria-hidden="true" />
                {t('fnAdmin.missingTranslation', 'Translation missing; showing original')}
              </p>
            )}
          </div>
        ),
      },
      {
        id: 'organization',
        header: t('field.organization'),
        accessorFn: (row) => organizationName(row.organizationId),
        meta: { hideBelow: 'md' },
        cell: ({ row }) => <span className="text-secondary wrap-anywhere">{organizationName(row.original.organizationId)}</span>,
      },
      {
        id: 'category',
        header: t('field.category'),
        enableSorting: false,
        meta: { hideBelow: 'lg' },
        cell: ({ row }) => {
          const value = options.data?.categories.find((item) => item.id === row.original.categoryId);
          const label = value ? localizedText(value.name, value.nameTranslations, language) : row.original.category;
          return label ? <Badge size="sm" tone="accent">{label}</Badge> : <span className="text-secondary">—</span>;
        },
      },
      {
        accessorKey: 'status',
        header: t('field.status'),
        cell: ({ row }) => (
          <span className="flex flex-wrap items-center gap-1.5">
            <FunctionStatusBadge status={row.original.status} />
            {row.original.revision && (
              <Link to={`/admin/functions/${row.original.revision.id}`} className="rounded-sm" lang={locale}>
                <StatusPill tone={row.original.revision.status === 'PENDING_REVIEW' ? 'pending' : 'draft'}>
                  {row.original.revision.status === 'PENDING_REVIEW' ? t('fnAdmin.revisionBadge.PENDING_REVIEW') : t('fnAdmin.revisionBadge.DRAFT')}
                </StatusPill>
              </Link>
            )}
          </span>
        ),
      },
      {
        id: 'verification',
        header: t('verification.column', 'Verification'),
        enableSorting: false,
        meta: { hideBelow: 'lg' },
        cell: ({ row }) => <VerificationBadge status={row.original.verificationStatus} />,
      },
      {
        id: 'languages',
        header: t('fnAdmin.languages'),
        enableSorting: false,
        meta: { hideBelow: 'xl' },
        cell: ({ row }) => {
          const filled = available.filter((item) => hasFunctionTranslation(row.original, item.code));
          const complete = filled.length === available.length;
          return (
            <Tooltip content={filled.map((item) => item.label).join(', ') || '—'}>
              <span tabIndex={0} className="inline-flex cursor-default items-center gap-1.5 rounded-pill border border-line px-2.5 py-1 text-xs font-medium tabular-nums text-secondary">
                <LanguagesIcon className="h-3 w-3" aria-hidden="true" />
                <span className={complete ? 'text-status-published' : undefined}>{filled.length}/{available.length}</span>
              </span>
            </Tooltip>
          );
        },
      },
      {
        id: 'actions',
        header: t('field.actions'),
        enableSorting: false,
        meta: { align: 'right', mobileHidden: true },
        cell: ({ row }) => (
          <Link to={`/admin/functions/${row.original.id}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            {hasPermission('FUNCTIONS_EDIT') && (isEditableStatus(row.original.status) || row.original.status === 'PUBLISHED') ? t('action.edit') : t('action.details')}
          </Link>
        ),
      },
    ],
    [t, language, locale, available, options.data, hasPermission, organizationName, user?.id],
  );

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('nav.functions')}
        description={t('fnAdmin.subtitle', 'Drafts, review queue and published services.')}
        actions={
          <>
            <TranslationBackfillButton />
            {hasPermission('FUNCTIONS_CREATE') && (
              <Link to="/admin/functions/new" className={buttonVariants({ variant: 'dark', size: 'sm' })}>
                <PlusIcon aria-hidden="true" />
                {t('fnAdmin.create')}
              </Link>
            )}
          </>
        }
      />

      <Card className="mb-6">
        <CardBody>
          <p className="micro mb-4 text-secondary">{t('fnAdmin.filters', 'Filters')}</p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-7">
            <Field label={t('field.status')}>
              {(control) => (
                <Select
                  {...control}
                  size="sm"
                  value={status}
                  onValueChange={setStatus}
                  options={[
                    { value: '', label: t('status.all') },
                    ...FUNCTION_STATUSES.map((value) => ({ value, label: functionStatusLabel(value, t) })),
                  ]}
                />
              )}
            </Field>
            <Field label={t('field.organization')}>
              {(control) => (
                <Select
                  {...control}
                  size="sm"
                  value={organization}
                  onValueChange={setOrganization}
                  options={[
                    { value: '', label: t('status.all') },
                    { value: 'unassigned', label: t('fnAdmin.unassigned', 'Unassigned') },
                    ...(options.data?.organizations ?? []).map((item) => ({ value: String(item.id), label: item.name })),
                  ]}
                />
              )}
            </Field>
            <Field label={t('field.category')}>
              {(control) => (
                <Select
                  {...control}
                  size="sm"
                  value={category}
                  onValueChange={setCategory}
                  options={[
                    { value: '', label: t('status.all') },
                    ...(options.data?.categories ?? []).map((item) => ({
                      value: String(item.id),
                      label: localizedText(item.name, item.nameTranslations, language),
                    })),
                  ]}
                />
              )}
            </Field>
            <Field label={t('verification.column', 'Verification')}>
              {(control) => (
                <Select
                  {...control}
                  size="sm"
                  value={verification}
                  onValueChange={(value) => setVerification(parseVerificationFilter(value))}
                  options={[
                    { value: '', label: t('status.all') },
                    { value: 'due', label: t('verification.filterDue', 'Needs recheck') },
                    { value: 'verified', label: t('verification.status.VERIFIED', 'Verified by organization') },
                  ]}
                />
              )}
            </Field>
            <Field label={t('assignee.title', 'Responsible staff member')}>
              {(control) => (
                <Select
                  {...control}
                  size="sm"
                  value={assignee}
                  onValueChange={setAssignee}
                  options={[
                    { value: '', label: t('status.all') },
                    { value: 'unassigned', label: t('assignments.state.UNASSIGNED', 'Unassigned') },
                    { value: 'me', label: t('reports.assignedToMe', 'Cards assigned to me') },
                  ]}
                />
              )}
            </Field>
            <Field label={t('field.language')}>
              {(control) => (
                <Select
                  {...control}
                  size="sm"
                  value={language}
                  onValueChange={setLanguage}
                  options={available.map((item) => ({ value: item.code, label: item.label }))}
                />
              )}
            </Field>
            <Field label={t('action.search')}>
              {(control) => <Input {...control} type="search" className="min-h-10 py-2 text-sm" value={search} onChange={(event) => setSearch(event.target.value)} />}
            </Field>
          </div>
          {Boolean(options.error) && <div className="mt-4"><FunctionErrorState error={options.error} onRetry={() => void options.refetch()} /></div>}
        </CardBody>
      </Card>

      <Card>
        {canView ? (
          <>
            <Toolbar summary={t('home.resultsCount', { count: rows.length })} />
            <DataTable
              columns={columns}
              data={rows}
              rowKey={(row) => row.id}
              caption={t('nav.functions')}
              pageSize={PAGE_SIZE}
              loading={functions.isPending}
              error={functions.error}
              onRetry={() => void functions.refetch()}
              empty={{ title: t('state.emptyTitle'), description: t('state.emptyText') }}
            />
          </>
        ) : (
          <FunctionErrorState error={forbidden} />
        )}
      </Card>
    </div>
  );
}
