import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookmarkIcon, Trash2Icon, XIcon } from 'lucide-react';
import { toast } from 'sonner';
import { PrintButton } from '../components/catalog/PrintButton';
import { SectionHeading } from '../components/home/SectionHeading';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { buttonVariants } from '../components/ui/buttonVariants';
import { Card } from '../components/ui/Card';
import { ConfirmModal } from '../components/ui/Modal';
import { SkeletonList } from '../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { usePublicFunctions } from '../features/functions/queries';
import { usePublicOrganizations } from '../features/organizations/queries';
import { useOrganizationNames } from '../features/organizations/useOrganizationNames';
import { useSavedServices } from '../features/saved/useSavedServices';
import { formatDate, truncate } from '../utils/format';
import { localizedText } from '../utils/translations';

export function SavedServices() {
  const { t, locale } = useI18n();
  const { items, remove, clear } = useSavedServices();
  const functions = usePublicFunctions();
  const organizations = usePublicOrganizations();
  const [confirmClear, setConfirmClear] = useState(false);

  const published = useMemo(() => new Map((functions.data ?? []).map((item) => [item.id, item])), [functions.data]);
  const organizationNames = useOrganizationNames(organizations.data);

  const clearAll = () => {
    clear();
    setConfirmClear(false);
    toast.success(t('saved.cleared', 'Saved services cleared'));
  };

  return (
    <div className="shell py-10 sm:py-14 lg:py-16">
      <SectionHeading
        eyebrow={t('nav.saved', 'Saved')}
        title={t('saved.title', 'Saved services')}
        description={t('saved.subtitle', 'Your list is stored only in this browser. It is not sent to the server and is not linked to you.')}
        aside={
          items.length > 0 ? (
            <div className="flex flex-wrap gap-2 print:hidden">
              <PrintButton label={t('saved.printList', 'Print list')} />
              <Button variant="danger" size="sm" icon={<Trash2Icon />} onClick={() => setConfirmClear(true)}>
                {t('saved.clearAll', 'Clear all')}
              </Button>
            </div>
          ) : undefined
        }
      />

      <div className="mt-8">
        {items.length === 0 ? (
          <Card>
            <EmptyState
              icon={<BookmarkIcon className="h-6 w-6" aria-hidden="true" />}
              title={t('saved.emptyTitle', 'You have not saved any services yet')}
              description={t('saved.emptyText', 'Use the Save button on a service to keep it here for later.')}
              action={
                <div className="flex flex-wrap justify-center gap-2">
                  <Link to="/#functions" className={buttonVariants({ variant: 'dark', size: 'sm' })}>{t('nav.catalog')}</Link>
                  <Link to="/finder" className={buttonVariants({ variant: 'outline', size: 'sm' })}>{t('nav.finder', 'Service finder')}</Link>
                </div>
              }
            />
          </Card>
        ) : functions.isPending ? (
          <SkeletonList count={Math.min(items.length, 5)} />
        ) : functions.error ? (
          <Card><ErrorState error={functions.error} onRetry={() => void functions.refetch()} /></Card>
        ) : (
          <ul className="border-b border-line" aria-label={t('saved.title', 'Saved services')}>
            {items.map((saved) => {
              const record = published.get(saved.id);
              const name = record ? localizedText(record.name, record.nameTranslations, locale) : saved.name;
              const description = record ? localizedText(record.description, record.descriptionTranslations, locale) : null;
              return (
                <li key={saved.id} className="flex items-start gap-4 border-t border-line py-5 break-inside-avoid">
                  <div className="min-w-0 flex-1">
                    {record ? (
                      <Link to={`/functions/${saved.id}`} lang={locale} className="rounded-sm text-xl font-medium text-foreground underline-offset-4 wrap-anywhere fine:hover:underline">
                        {name}
                      </Link>
                    ) : (
                      <p className="text-xl font-medium text-secondary wrap-anywhere">{name}</p>
                    )}
                    {description && <p lang={locale} className="mt-1.5 text-sm leading-6 text-secondary wrap-anywhere">{truncate(description, 160)}</p>}
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      {record ? (
                        <Badge size="sm">{organizationNames.get(record.organizationId) ?? t('fn.organizationUnknown', 'Organization unknown')}</Badge>
                      ) : (
                        <Badge size="sm">{t('saved.unavailable', 'No longer published')}</Badge>
                      )}
                      <span className="text-xs tabular-nums text-secondary">
                        {t('saved.savedOn', 'Saved on')} {formatDate(saved.savedAt, locale)}
                      </span>
                    </div>
                  </div>
                  <Button
                    variant="ghost"
                    size="iconSm"
                    onClick={() => remove(saved.id)}
                    aria-label={`${t('saved.remove', 'Remove from saved')}: ${name}`}
                    className="print:hidden"
                  >
                    <XIcon aria-hidden="true" />
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <ConfirmModal
        open={confirmClear}
        title={t('saved.clearAll', 'Clear all')}
        message={t('saved.clearConfirm', 'Remove every saved service from this browser?')}
        confirmLabel={t('saved.clearAll', 'Clear all')}
        cancelLabel={t('action.cancel', 'Cancel')}
        onConfirm={clearAll}
        onClose={() => setConfirmClear(false)}
      />
    </div>
  );
}
