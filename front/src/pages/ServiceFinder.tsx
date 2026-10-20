import { useDeferredValue, useId, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { CompassIcon, LifeBuoyIcon, ListIcon, RotateCcwIcon, SearchIcon, XIcon } from 'lucide-react';
import { FunctionListRow } from '../components/catalog/FunctionListRow';
import { SectionHeading } from '../components/home/SectionHeading';
import { Button } from '../components/ui/Button';
import { buttonVariants } from '../components/ui/buttonVariants';
import { Card, CardBody } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { SkeletonList } from '../components/ui/Skeleton';
import { EmptyState, ErrorState, NoResultsState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { useFunctionCategories, usePublicFunctions } from '../features/functions/queries';
import { usePublicOrganizations } from '../features/organizations/queries';
import { useOrganizationNames } from '../features/organizations/useOrganizationNames';
import { cn } from '../lib/cn';
import type { TranslatedText } from '../types/api';
import { FINDER_INTENTS, matchesIntent, rankServices, type MatchField, type RankingContext } from '../utils/finderRanking';
import { searchTerms } from '../utils/search';
import { localizedText } from '../utils/translations';
import { usePatchSearchParams } from '../hooks/usePatchSearchParams';

const RESULT_LIMIT = 50;

/** Plain-language examples under the search field; each is a real query of the same search. */
const EXAMPLES = [
  { key: 'finder.example.certificate', fallback: 'get a certificate' },
  { key: 'finder.example.replace', fallback: 'replace a document' },
  { key: 'finder.example.marriage', fallback: 'register a marriage' },
];

const REASON_LABELS: Record<MatchField, [string, string]> = {
  exactName: ['finder.reason.exactName', 'service name'],
  name: ['finder.reason.name', 'service name'],
  translatedName: ['finder.reason.translatedName', 'name in another language'],
  synonym: ['finder.reason.synonym', 'similar word in the name'],
  description: ['finder.reason.description', 'description'],
  organization: ['finder.reason.organization', 'organization'],
  instructions: ['finder.reason.instructions', 'instructions and documents'],
};

/**
 * Public service finder: a plain-language search first, with results while the visitor types, optional filters and a
 * simple guided path by everyday intent. Only published services (GET /api/functions) are ever listed; nothing typed
 * here is sent or stored.
 */
const allNames = (item: { name: string; nameTranslations?: Record<string, TranslatedText> }) =>
  [item.name, ...Object.values(item.nameTranslations ?? {}).map((value) => value?.text)];

export function ServiceFinder() {
  const { t, locale } = useI18n();
  const functions = usePublicFunctions();
  const categories = useFunctionCategories();
  const organizations = usePublicOrganizations();
  const [params, update, setParams] = usePatchSearchParams();
  const searchId = useId();
  const resultsId = useId();

  const query = params.get('q') ?? '';
  const deferredQuery = useDeferredValue(query);
  const intentKey = params.get('intent') ?? '';
  const intent = FINDER_INTENTS.find((item) => item.key === intentKey);
  const organizationId = params.get('organization') ?? '';
  const categoryId = params.get('category') ?? '';
  const showAll = params.get('all') === '1';

  const published = useMemo(() => functions.data ?? [], [functions.data]);
  const organizationName = useOrganizationNames(organizations.data);
  const context = useMemo<RankingContext>(() => {
    const organizationNames = new Map((organizations.data ?? []).map((item) => [item.id, allNames(item)]));
    const categoryNames = new Map((categories.data ?? []).map((item) => [item.id, allNames(item)]));
    return {
      locale,
      organizationNames: (id) => organizationNames.get(id) ?? [],
      categoryNames: (id) => (id == null ? [] : categoryNames.get(id) ?? []),
    };
  }, [locale, organizations.data, categories.data]);

  const filtered = useMemo(
    () => published.filter((item) => (!organizationId || item.organizationId === Number(organizationId))
      && (!categoryId || item.categoryId === Number(categoryId))),
    [published, organizationId, categoryId],
  );
  const intentCounts = useMemo(
    () => new Map(FINDER_INTENTS.map((item) => [item.key, published.filter((service) => matchesIntent(service, item, context)).length])),
    [published, context],
  );
  const hasQuery = searchTerms(deferredQuery).length > 0;
  const active = hasQuery || Boolean(intent) || showAll || Boolean(organizationId) || Boolean(categoryId);
  const results = useMemo(() => {
    if (!active) return [];
    const pool = intent ? filtered.filter((item) => matchesIntent(item, intent, context)) : filtered;
    return rankServices(pool, deferredQuery, context);
  }, [active, filtered, intent, deferredQuery, context]);

  const organizationOptions = useMemo(() => {
    const counts = new Map<number, number>();
    published.forEach((item) => counts.set(item.organizationId, (counts.get(item.organizationId) ?? 0) + 1));
    return [...counts.keys()]
      .map((id) => ({ value: String(id), label: organizationName.get(id) ?? `#${id}` }))
      .sort((a, b) => a.label.localeCompare(b.label, locale));
  }, [published, organizationName, locale]);
  const categoryOptions = useMemo(() => {
    const ids = new Set(published.map((item) => item.categoryId).filter((id): id is number => id != null));
    return [...ids]
      .map((id) => {
        const found = categories.data?.find((item) => item.id === id);
        return { value: String(id), label: found ? localizedText(found.name, found.nameTranslations, locale) : `#${id}` };
      })
      .sort((a, b) => a.label.localeCompare(b.label, locale));
  }, [published, categories.data, locale]);

  const reasonText = (reasons: MatchField[]) => {
    const labels = [...new Set(reasons.map((reason) => t(...REASON_LABELS[reason])))];
    return labels.length ? `${t('finder.matchedBy', 'Matches')}: ${labels.join(', ')}` : undefined;
  };

  const filtersSet = Boolean(organizationId || categoryId);
  const clearFilters = () => update({ organization: '', category: '' });
  const startOver = () => setParams(new URLSearchParams(), { replace: true });

  const loading = functions.isPending || categories.isPending || organizations.isPending;
  const loadError = functions.error ?? categories.error ?? organizations.error;
  const availableIntents = FINDER_INTENTS.filter((item) => (intentCounts.get(item.key) ?? 0) > 0);

  return (
    <div className="shell py-10 sm:py-14 lg:py-16">
      <SectionHeading
        eyebrow={t('finder.eyebrow', 'Service finder')}
        title={t('finder.searchTitle', 'Find the service you need')}
        description={t('finder.searchSubtitle', 'Describe what you want to do in your own words, or choose what you need below. Nothing you type is sent or stored, and you do not need an account.')}
      />

      <Card className="mt-8">
        {loading ? (
          <CardBody><SkeletonList count={3} /></CardBody>
        ) : loadError ? (
          <ErrorState error={loadError} onRetry={() => { void functions.refetch(); void categories.refetch(); void organizations.refetch(); }} />
        ) : published.length === 0 ? (
          <EmptyState
            icon={<CompassIcon className="h-6 w-6" aria-hidden="true" />}
            title={t('finder.emptyCatalogTitle', 'No services are published yet')}
            description={t('finder.emptyCatalogText', 'Please check again later or browse the organizations.')}
            action={<Link to="/#organizations" className={buttonVariants({ variant: 'outline', size: 'sm' })}>{t('nav.organizations')}</Link>}
          />
        ) : (
          <CardBody className="space-y-6 sm:p-8">
            <form role="search" onSubmit={(event) => event.preventDefault()} className="space-y-3">
              <label htmlFor={searchId} className="block text-lg font-semibold text-foreground">
                {t('finder.searchLabel', 'What do you want to do?')}
              </label>
              <div className="relative">
                <SearchIcon className="pointer-events-none absolute start-4 top-1/2 h-5 w-5 -translate-y-1/2 text-secondary" aria-hidden="true" />
                <Input
                  id={searchId}
                  type="search"
                  value={query}
                  autoComplete="off"
                  maxLength={120}
                  aria-controls={resultsId}
                  placeholder={t('finder.searchPlaceholder', 'For example: get a certificate')}
                  onChange={(event) => update({ q: event.target.value, all: '' })}
                  className="min-h-14 ps-12 pe-12 text-base [&::-webkit-search-cancel-button]:hidden"
                />
                {query && (
                  <button type="button" onClick={() => update({ q: '' })} aria-label={t('finder.clearSearch', 'Clear search')}
                    className="absolute end-2 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-pill text-secondary fine:hover:bg-surface-2 fine:hover:text-foreground">
                    <XIcon className="h-4 w-4" aria-hidden="true" />
                  </button>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2 text-sm text-secondary">
                <span>{t('finder.examplesLabel', 'Try')}:</span>
                {EXAMPLES.map((example) => (
                  <Button key={example.key} variant="light" size="sm" onClick={() => update({ q: t(example.key, example.fallback), intent: '', all: '' })}>
                    {t(example.key, example.fallback)}
                  </Button>
                ))}
              </div>
            </form>

            <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] sm:items-end">
              <Select
                size="sm"
                value={organizationId}
                aria-label={t('finder.organizationFilter', 'Organization (optional)')}
                onValueChange={(value) => update({ organization: value })}
                options={[{ value: '', label: t('finder.anyOrganization', 'Any organization') }, ...organizationOptions]}
              />
              <Select
                size="sm"
                value={categoryId}
                aria-label={t('finder.categoryFilter', 'Area (optional)')}
                onValueChange={(value) => update({ category: value })}
                options={[{ value: '', label: t('finder.anyArea', 'Any area') }, ...categoryOptions]}
              />
              {filtersSet ? (
                <Button variant="ghost" size="sm" icon={<RotateCcwIcon />} onClick={clearFilters}>{t('finder.clearFilters', 'Clear filters')}</Button>
              ) : <span aria-hidden="true" />}
            </div>

            {intent && (
              <p className="flex flex-wrap items-center gap-2 rounded-control bg-surface px-4 py-3 text-sm text-foreground">
                <span>{t('finder.yourAnswer', 'Your answer')}: <span className="font-semibold">{t(intent.labelKey, intent.label)}</span></span>
                <Button variant="outline" size="sm" onClick={() => update({ intent: '' })}>{t('finder.changeAnswer', 'Change answer')}</Button>
              </p>
            )}

            {!active ? (
              <fieldset className="space-y-4 border-t border-line pt-6">
                <legend className="text-lg font-semibold text-foreground">{t('finder.notSure', 'Not sure what to search for? Choose what you need')}</legend>
                <div className="grid gap-2 sm:grid-cols-2">
                  {availableIntents.map((item) => (
                    <button
                      key={item.key}
                      type="button"
                      onClick={() => update({ intent: item.key, all: '' })}
                      className="flex min-h-14 items-center justify-between gap-3 rounded-card-sm border border-line bg-background px-4 py-3 text-start text-foreground transition-colors duration-snap fine:hover:bg-surface focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                    >
                      <span className="min-w-0 font-medium wrap-anywhere">{t(item.labelKey, item.label)}</span>
                      <span className="shrink-0 text-xs tabular-nums text-secondary">{t('catalog.servicesCount', { count: intentCounts.get(item.key) ?? 0 })}</span>
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => update({ all: '1' })}
                    className="flex min-h-14 items-center justify-between gap-3 rounded-card-sm border border-dashed border-line bg-background px-4 py-3 text-start text-foreground transition-colors duration-snap fine:hover:bg-surface focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                  >
                    <span className="flex items-center gap-2 font-medium"><ListIcon className="h-4 w-4" aria-hidden="true" />{t('finder.showAll', 'Show all services')}</span>
                    <span className="shrink-0 text-xs tabular-nums text-secondary">{t('catalog.servicesCount', { count: published.length })}</span>
                  </button>
                </div>
              </fieldset>
            ) : (
              <section id={resultsId} aria-labelledby={`${resultsId}-title`} className="space-y-4 border-t border-line pt-6">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <h2 id={`${resultsId}-title`} className="text-lg font-semibold text-foreground">{t('finder.resultsHeading', 'Matching services')}</h2>
                  <p className="text-sm tabular-nums text-secondary" aria-live="polite">
                    {t('finder.resultsFound', { count: results.length })}
                  </p>
                </div>
                {results.length === 0 ? (
                  <div className="space-y-4">
                    <NoResultsState
                      title={t('finder.noResultsTitle2', 'No published service matches this')}
                      description={t('finder.noResultsHelp', 'Try fewer or more general words, check the spelling, or describe the result you need (for example “certificate” instead of the document number).')}
                    />
                    <div className="flex flex-wrap justify-center gap-2">
                      {query && <Button variant="outline" size="sm" onClick={() => update({ q: '' })}>{t('finder.clearSearch', 'Clear search')}</Button>}
                      {filtersSet && <Button variant="outline" size="sm" onClick={clearFilters}>{t('finder.clearFilters', 'Clear filters')}</Button>}
                      {intent && <Button variant="outline" size="sm" onClick={() => update({ intent: '' })}>{t('finder.changeAnswer', 'Change answer')}</Button>}
                      <Button variant="outline" size="sm" icon={<ListIcon />} onClick={() => setParams(new URLSearchParams({ all: '1' }), { replace: true })}>
                        {t('finder.showAll', 'Show all services')}
                      </Button>
                      <Link to="/support" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
                        <LifeBuoyIcon className="h-4 w-4" aria-hidden="true" />{t('finder.askSupport', 'Ask support for help')}
                      </Link>
                    </div>
                    {availableIntents.length > 0 && (
                      <div className="text-center text-sm text-secondary">
                        <p className="mb-2">{t('finder.orChoose', 'Or choose what you need:')}</p>
                        <ul className="flex flex-wrap justify-center gap-2">
                          {availableIntents.map((item) => (
                            <li key={item.key}>
                              <Button variant="light" size="sm" onClick={() => setParams(new URLSearchParams({ intent: item.key }), { replace: true })}>
                                {t(item.labelKey, item.label)}
                              </Button>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                ) : (
                  <>
                    <ol className={cn('border-b border-line')}>
                      {results.slice(0, RESULT_LIMIT).map((ranked, position) => (
                        <FunctionListRow
                          key={ranked.item.id}
                          item={ranked.item}
                          index={position + 1}
                          organizationName={organizationName.get(ranked.item.organizationId)}
                          reason={reasonText(ranked.reasons)}
                        />
                      ))}
                    </ol>
                    {results.length > RESULT_LIMIT && (
                      <p className="text-sm text-secondary">{t('finder.moreResults', 'More services match. Add a word or a filter to narrow the list.')}</p>
                    )}
                  </>
                )}
                <div className="flex flex-wrap gap-2 border-t border-line pt-5">
                  <Button variant="outline" icon={<RotateCcwIcon />} onClick={startOver}>{t('finder.restart', 'Start over')}</Button>
                </div>
              </section>
            )}
          </CardBody>
        )}
      </Card>
    </div>
  );
}
