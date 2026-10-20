import { useMemo, useState } from 'react';
import { RotateCcwIcon } from 'lucide-react';
import { Hero } from '../components/home/Hero';
import { NumbersPanel } from '../components/home/NumbersPanel';
import { SectionHeading } from '../components/home/SectionHeading';
import { OrganizationInkCard } from '../components/catalog/OrganizationInkCard';
import { FunctionListRow } from '../components/catalog/FunctionListRow';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Reveal } from '../components/ui/Reveal';
import { Select } from '../components/ui/Select';
import { SearchInput } from '../components/ui/SearchInput';
import { SkeletonCards, SkeletonList } from '../components/ui/Skeleton';
import { ErrorState, NoResultsState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { usePublicOrganizations } from '../features/organizations/queries';
import { useOrganizationNames } from '../features/organizations/useOrganizationNames';
import { usePublicFunctions } from '../features/functions/queries';
import { usePageView } from '../features/engagement/tracking';
import { useRegions } from '../features/reference/queries';
import { matchesTerms, searchTerms, serviceHaystack } from '../utils/finder';

export function Home() {
  const { t, locale } = useI18n();
  const [query, setQuery] = useState('');
  const [organizationId, setOrganizationId] = useState('');
  const [category, setCategory] = useState('');

  const organizations = usePublicOrganizations();
  const functions = usePublicFunctions();
  const regions = useRegions();
  usePageView('CATALOG_VIEW');

  const orgList = useMemo(() => organizations.data ?? [], [organizations.data]);
  const fnList = useMemo(() => functions.data ?? [], [functions.data]);

  const orgName = useOrganizationNames(organizations.data);

  /** Organization names in every language, so a query in any supported script matches. */
  const orgSearchText = useMemo(() => {
    const map = new Map<number, string[]>();
    orgList.forEach((item) => map.set(item.id, [item.name, ...Object.values(item.nameTranslations ?? {}).map((value) => value.text)]));
    return map;
  }, [orgList]);

  const indexes = useMemo(() => new Map(fnList.map((item) => [item.id, serviceHaystack(item, locale, orgSearchText.get(item.organizationId) ?? [])])), [fnList, locale, orgSearchText]);

  const categories = useMemo(
    () => Array.from(new Set(fnList.map((item) => item.category).filter((value): value is string => Boolean(value)))).sort(),
    [fnList],
  );

  const filtered = useMemo(() => {
    const terms = searchTerms(query, locale);
    return fnList.filter((item) => {
      if (organizationId && item.organizationId !== Number(organizationId)) return false;
      if (category && item.category !== category) return false;
      if (terms.length === 0) return true;
      const index = indexes.get(item.id);
      return index ? matchesTerms(index, terms) : false;
    });
  }, [fnList, organizationId, category, query, indexes, locale]);

  const functionCountByOrg = useMemo(() => {
    const map = new Map<number, number>();
    fnList.forEach((item) => map.set(item.organizationId, (map.get(item.organizationId) ?? 0) + 1));
    return map;
  }, [fnList]);

  const hasFilters = Boolean(query || organizationId || category);

  const resetFilters = () => {
    setQuery('');
    setOrganizationId('');
    setCategory('');
  };

  return (
    <div className="w-full">
      <Hero />

      <section className="shell py-14 sm:py-20" aria-label={t('home.heroBadge')}>
        <NumbersPanel
          stats={{
            organizations: organizations.data ? orgList.length : null,
            functions: functions.data ? fnList.length : null,
            regions: regions.data ? regions.data.length : null,
          }}
        />
      </section>

      <section id="functions" tabIndex={-1} className="shell scroll-mt-24 pb-14 focus:outline-none sm:pb-20">
        <Reveal>
          <SectionHeading
            eyebrow={t('nav.catalog')}
            title={t('home.catalogTitle')}
            description={t('home.catalogSubtitle')}
            aside={
              <p aria-live="polite" className="inline-flex min-h-10 w-fit items-center rounded-pill border border-line px-4 text-sm font-medium tabular-nums text-secondary">
                {filtered.length} {t('home.resultsCount')}
              </p>
            }
          />
        </Reveal>

        <Reveal index={1} className="mt-8">
          <div className="grid gap-3 rounded-card-sm border border-line bg-background p-3 md:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_14rem_13rem_auto] lg:items-center">
            <SearchInput
              value={query}
              onChange={setQuery}
              label={t('action.search')}
              placeholder={t('home.heroSearchPlaceholder')}
              className="md:col-span-2 lg:col-span-1"
            />
            <Select
              size="sm"
              aria-label={t('field.organization')}
              value={organizationId}
              onValueChange={setOrganizationId}
              options={[
                { value: '', label: `${t('field.organization')} · ${t('status.all')}` },
                ...orgList.map((item) => ({ value: String(item.id), label: orgName.get(item.id) ?? item.name })),
              ]}
            />
            <Select
              size="sm"
              aria-label={t('field.category')}
              value={category}
              onValueChange={setCategory}
              options={[
                { value: '', label: `${t('field.category')} · ${t('status.all')}` },
                ...categories.map((item) => ({ value: item, label: item })),
              ]}
            />
            <div className="flex md:justify-end lg:justify-start">
              {hasFilters && (
                <Button variant="ghost" size="sm" className="w-full md:w-auto" icon={<RotateCcwIcon />} onClick={resetFilters}>
                  {t('action.reset')}
                </Button>
              )}
            </div>
          </div>
        </Reveal>

        <div className="mt-8">
          {functions.isPending ? (
            <SkeletonList count={5} />
          ) : functions.error ? (
            <Card><ErrorState error={functions.error} onRetry={() => void functions.refetch()} /></Card>
          ) : filtered.length === 0 ? (
            <Card><NoResultsState title={t('state.emptyTitle')} description={t('state.emptyText')} /></Card>
          ) : (
            <ol className="border-b border-line">
              {filtered.map((item, index) => (
                <FunctionListRow key={item.id} item={item} index={index + 1} organizationName={orgName.get(item.organizationId)} />
              ))}
            </ol>
          )}
        </div>
      </section>

      <section id="organizations" tabIndex={-1} className="scroll-mt-24 bg-surface py-14 focus:outline-none sm:py-20">
        <div className="shell">
          <Reveal>
            <SectionHeading eyebrow={t('nav.organizations')} title={t('home.orgsTitle')} description={t('home.orgsSubtitle')} />
          </Reveal>
          <div className="mt-8">
            {organizations.isPending ? (
              <SkeletonCards count={6} />
            ) : organizations.error ? (
              <Card><ErrorState error={organizations.error} onRetry={() => void organizations.refetch()} /></Card>
            ) : orgList.length === 0 ? (
              <Card><NoResultsState title={t('state.emptyTitle')} /></Card>
            ) : (
              <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {orgList.map((item, index) => (
                  <Reveal as="li" key={item.id} index={index} className="min-w-0">
                    <OrganizationInkCard organization={item} functionCount={functionCountByOrg.get(item.id) ?? 0} />
                  </Reveal>
                ))}
              </ul>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
