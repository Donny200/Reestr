import { useMemo, useState } from 'react';
import type { ColumnDef } from '@tanstack/react-table';
import { CheckIcon, GlobeIcon, LanguagesIcon, PlusIcon, SearchIcon, Trash2Icon } from 'lucide-react';
import { toast } from 'sonner';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Toolbar } from '../../components/ui/Toolbar';
import { DataTable } from '../../components/ui/DataTable';
import { Badge } from '../../components/ui/Badge';
import { ConfirmModal } from '../../components/ui/Modal';
import { SkeletonText } from '../../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { useAddLanguage, useDeleteLanguage, useLanguages } from '../../features/languages/queries';
import { useLanguageSearch } from '../../features/languages/search';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import type { Language } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { languageDisplayName } from '../../lib/i18n/locale';
import { TranslationCoverageCard } from '../../features/languages/TranslationCoverageCard';

export function Languages() {
  const { t, locale, reloadLanguages } = useI18n();
  const languages = useLanguages();
  const add = useAddLanguage();
  const remove = useDeleteLanguage();
  const [query, setQuery] = useState('');
  const [selectedCode, setSelectedCode] = useState('');
  const [deleting, setDeleting] = useState<Language | null>(null);
  const debouncedQuery = useDebouncedValue(query.trim());
  const results = useLanguageSearch(debouncedQuery);

  const existingCodes = useMemo(() => new Set((languages.data ?? []).map((item) => item.code.toLowerCase())), [languages.data]);
  const searching = debouncedQuery !== query.trim() || (results.isFetching && !results.data);
  const directCode = !searching && !results.error && results.data?.length === 0 ? query.trim() : '';
  const codeToAdd = selectedCode || directCode;
  const codeAlreadyAdded = existingCodes.has(codeToAdd.toLowerCase());

  const submitAdd = () => {
    if (!codeToAdd || codeAlreadyAdded) return;
    add.mutate(codeToAdd, {
      onSuccess: async () => {
        toast.success(t('toast.created'));
        setSelectedCode('');
        setQuery('');
        await reloadLanguages();
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    });
  };

  const submitDelete = () => {
    if (!deleting) return;
    remove.mutate(deleting.id, {
      onSuccess: async () => {
        toast.success(t('toast.deleted'));
        setDeleting(null);
        await reloadLanguages();
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    });
  };

  const columns = useMemo<ColumnDef<Language>[]>(
    () => [
      {
        accessorKey: 'id',
        header: t('field.id'),
        meta: { hideBelow: 'xl', mobileHidden: true },
        cell: ({ row }) => <span className="tabular-nums text-secondary">#{row.original.id}</span>,
      },
      {
        accessorKey: 'code',
        header: t('lang.code', 'Code (BCP 47)'),
        cell: ({ row }) => <Badge size="sm">{row.original.code}</Badge>,
      },
      {
        accessorKey: 'nativeName',
        header: t('lang.nativeName', 'Native name'),
        cell: ({ row }) => (
          <span className="min-w-0">
            <span className="block font-medium text-foreground">{row.original.nativeName}</span>
            <span className="block text-xs text-secondary">{languageDisplayName(row.original.code, locale)}</span>
          </span>
        ),
      },
      {
        accessorKey: 'defaultLanguage',
        header: t('lang.default'),
        meta: { hideBelow: 'md' },
        cell: ({ row }) =>
          row.original.defaultLanguage ? <Badge size="sm" tone="accent">{t('lang.default')}</Badge> : <span className="text-secondary">—</span>,
      },
      {
        id: 'actions',
        header: t('field.actions'),
        enableSorting: false,
        meta: { align: 'right', mobileHidden: true },
        cell: ({ row }) =>
          row.original.defaultLanguage ? (
            <span className="text-xs text-secondary">{t('lang.default')}</span>
          ) : (
            <Button variant="ghost" size="iconSm" aria-label={`${t('action.delete')}: ${row.original.nativeName}`} className="text-danger fine:hover:bg-status-danger-bg fine:hover:text-danger" onClick={() => setDeleting(row.original)}>
              <Trash2Icon aria-hidden="true" />
            </Button>
          ),
      },
    ],
    [t, locale],
  );

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('lang.title')}
        description={t('lang.current')}
        badge={<Badge size="sm" tone="accent">{(languages.data ?? []).length}</Badge>}
      />

      <div className="grid gap-6 xl:grid-cols-5">
        <Card className="xl:col-span-3">
          <Toolbar summary={t('home.resultsCount', { count: (languages.data ?? []).length })} />
          <DataTable
            columns={columns}
            data={languages.data ?? []}
            rowKey={(row) => row.id}
            caption={t('lang.current')}
            loading={languages.isPending}
            error={languages.error}
            onRetry={() => void languages.refetch()}
            pageSize={25}
            initialSorting={[{ id: 'nativeName', desc: false }]}
            empty={{ title: t('state.emptyTitle') }}
          />
        </Card>

        <div className="space-y-6 xl:col-span-2">
        <Card className="h-fit">
          <CardHeader title={t('lang.searchTitle')} icon={<LanguagesIcon className="h-4 w-4" aria-hidden="true" />} />
          <CardBody className="space-y-4">
            <label className="block">
              <span className="block text-sm font-medium text-foreground">{t('lang.searchPlaceholder')}</span>
              <span className="relative mt-2 block">
                <SearchIcon className="pointer-events-none absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-secondary" aria-hidden="true" />
                <Input
                  value={query}
                  onChange={(event) => {
                    setQuery(event.target.value);
                    setSelectedCode('');
                  }}
                  placeholder={t('lang.searchPlaceholder')}
                  className="ps-9"
                />
              </span>
            </label>

            <div className="min-h-36" aria-live="polite">
              {query.trim() === '' ? (
                <p className="px-1 text-sm text-secondary">{t('lang.searchPlaceholder')}</p>
              ) : searching ? (
                <SkeletonText lines={4} />
              ) : results.error ? (
                <ErrorState error={results.error} onRetry={() => void results.refetch()} />
              ) : (results.data ?? []).length === 0 ? (
                <EmptyState title={t('state.emptyTitle')} icon={<GlobeIcon className="h-6 w-6" aria-hidden="true" />} />
              ) : (
                <ul className="max-h-64 space-y-1 overflow-y-auto pe-1">
                  {(results.data ?? []).map((item) => {
                    const already = item.alreadyAdded || existingCodes.has(item.code.toLowerCase());
                    const selected = selectedCode === item.code;
                    return (
                      <li key={item.code}>
                        <button
                          type="button"
                          disabled={already}
                          aria-pressed={selected}
                          onClick={() => setSelectedCode(item.code)}
                          className={cn(
                            'flex min-h-14 w-full items-center justify-between gap-3 rounded-control border px-3 py-2.5 text-start transition-colors duration-snap disabled:cursor-not-allowed disabled:text-secondary',
                            selected ? 'border-ink bg-surface' : 'border-line bg-background fine:hover:bg-surface',
                          )}
                        >
                          <span className="min-w-0">
                            <span className="block truncate text-sm font-medium text-foreground">{item.nativeName}</span>
                            <span className="block truncate text-xs text-secondary">{item.code} · {item.name}</span>
                          </span>
                          {already ? (
                            <Badge size="sm">{t('lang.alreadyAdded', 'Already added')}</Badge>
                          ) : selected ? (
                            <CheckIcon className="h-4 w-4 shrink-0 text-accent-text" aria-hidden="true" />
                          ) : null}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            <Button className="w-full" onClick={submitAdd} disabled={!codeToAdd || codeAlreadyAdded} loading={add.isPending} icon={<PlusIcon />}>
              {t('lang.addSelected')}
            </Button>
            <p className="text-xs leading-5 text-secondary">{t('lang.addHint')}</p>
          </CardBody>
        </Card>
        <TranslationCoverageCard />
        </div>
      </div>

      <ConfirmModal
        open={Boolean(deleting)}
        title={t('action.delete')}
        message={`${deleting?.nativeName ?? ''} — ${t('lang.deleteConfirm')}`}
        confirmLabel={t('action.delete')}
        cancelLabel={t('action.cancel')}
        loading={remove.isPending}
        onConfirm={submitDelete}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
