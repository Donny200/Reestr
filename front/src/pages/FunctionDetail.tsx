import { useRef } from 'react';
import { CalendarPlusIcon, LanguagesIcon } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { BackLink } from '../components/catalog/BackLink';
import { OrganizationContactDetails } from '../components/catalog/OrganizationContactDetails';
import { GuideDownloadLink } from '../components/catalog/GuideDownloadLink';
import { PrintButton } from '../components/catalog/PrintButton';
import { ServiceInstructionsView } from '../components/catalog/ServiceInstructionsView';
import { ShareButton } from '../components/catalog/ShareButton';
import { VerificationNotice } from '../components/catalog/VerificationNotice';
import { VisitSheet } from '../components/catalog/VisitSheet';
import { buttonVariants } from '../components/ui/buttonVariants';
import { useAvailability, usePublicSite } from '../features/appointments/queries';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Eyebrow } from '../components/ui/Eyebrow';
import { Reveal } from '../components/ui/Reveal';
import { SkeletonText } from '../components/ui/Skeleton';
import { ErrorState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { usePublicFunction } from '../features/functions/queries';
import { usePublicOrganizations } from '../features/organizations/queries';
import { useEngagementTracker, usePageView, usePrintTracking } from '../features/engagement/tracking';
import { ReportProblemButton } from '../features/reports/ReportProblemButton';
import { SaveServiceButton } from '../features/saved/SaveServiceButton';
import { FeedbackPrompt } from '../features/feedback/FeedbackPrompt';
import { AskAboutServiceLink } from '../features/help/AskAboutServiceLink';
import { ServiceHistory } from '../features/history/ServiceHistory';
import type { CatalogFunction } from '../types/api';
import { hasContactDetails } from '../utils/contact';
import { formatDate } from '../utils/format';
import { INSTRUCTION_FIELDS, localizedInstruction } from '../utils/instructions';
import { localizedText } from '../utils/translations';

function translationState(record: CatalogFunction, locale: string) {
  const language = locale.toLowerCase();
  if (record.sourceLanguage && record.sourceLanguage.toLowerCase() === language) return { machine: false, fallback: false };
  const texts = [record.nameTranslations?.[language], record.description ? record.descriptionTranslations?.[language] : undefined];
  const instructions = INSTRUCTION_FIELDS.map((field) => localizedInstruction(record, field.key, locale)).filter((value) => value !== null);
  return {
    machine: texts.some((text) => text?.source === 'machine') || instructions.some((value) => value.machine),
    fallback: !record.nameTranslations?.[language]?.text || instructions.some((value) => value.fallback),
  };
}

export function FunctionDetail() {
  const { id } = useParams<{ id: string }>();
  const functionId = Number(id);
  const { t, locale } = useI18n();

  const item = usePublicFunction(functionId);
  const organizations = usePublicOrganizations();
  const track = useEngagementTracker();
  const serviceId = item.data?.id;
  usePageView(serviceId === undefined ? null : 'SERVICE_VIEW', serviceId);
  usePrintTracking(serviceId ?? null);

  const organization = (organizations.data ?? []).find((org) => org.id === item.data?.organizationId);
  const functionName = localizedText(item.data?.name, item.data?.nameTranslations, locale) ?? '';
  const functionDescription = localizedText(item.data?.description, item.data?.descriptionTranslations, locale);
  const organizationName = localizedText(organization?.name, organization?.nameTranslations, locale);
  const state = item.data ? translationState(item.data, locale) : { machine: false, fallback: false };
  const reportButtonRef = useRef<HTMLDivElement>(null);
  const publicSite = usePublicSite();
  const availability = useAvailability(functionId, undefined, Boolean(item.data));

  return (
    <div className="shell py-8 sm:py-12 lg:py-16 print:py-0">
      {item.data && <VisitSheet record={item.data} organization={organization} publicBaseUrl={publicSite.data?.publicBaseUrl} />}
      <div className="print:hidden">
        <BackLink to="/#functions" label={t('nav.functions')} />
      </div>

      {item.isPending ? (
        <Card size="lg" className="mt-6 p-8 sm:p-10"><SkeletonText lines={6} /></Card>
      ) : item.error ? (
        <Card size="lg" className="mt-6"><ErrorState error={item.error} onRetry={() => void item.refetch()} /></Card>
      ) : item.data ? (
        <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.65fr)_minmax(18rem,0.85fr)] lg:items-start print:hidden">
          <Reveal as="article" className="min-w-0">
            <Eyebrow className="print:hidden">{t('nav.functions')}</Eyebrow>
            <h1 lang={locale} className="mt-4 text-4xl font-semibold text-foreground wrap-anywhere sm:text-5xl print:mt-0 print:text-3xl">{functionName}</h1>
            {organizationName && <p lang={locale} className="mt-3 hidden text-base print:block">{organizationName}</p>}

            <div className="mt-6 flex flex-wrap items-center gap-2 print:hidden">
              <SaveServiceButton service={{ id: item.data.id, name: functionName }} />
              <ShareButton title={functionName} path={`/functions/${item.data.id}`} />
              {availability.data?.bookable && (
                <Link to={`/functions/${item.data.id}/book`} className={buttonVariants({ variant: 'dark', size: 'sm' })}>
                  <CalendarPlusIcon className="h-4 w-4" aria-hidden="true" />{t('appointments.book', 'Book an appointment')}
                </Link>
              )}
              <PrintButton label={t('visitSheet.print', 'Print visit sheet')} />
              <GuideDownloadLink serviceId={item.data.id} />
              <AskAboutServiceLink serviceId={item.data.id} />
              <div ref={reportButtonRef} className="contents">
                <ReportProblemButton entityType="FUNCTION" entityId={item.data.id} />
              </div>
            </div>
            {item.data.lastUpdatedAt && (
              <p className="mt-4 text-sm tabular-nums text-secondary">
                {t('history.lastUpdated', 'Last updated')}: <time dateTime={item.data.lastUpdatedAt}>{formatDate(item.data.lastUpdatedAt, locale)}</time>
              </p>
            )}

            {(state.machine || state.fallback) && (
              <p className="mt-6 flex items-start gap-2.5 rounded-control bg-surface px-4 py-3 text-sm leading-6 text-secondary print:hidden">
                <LanguagesIcon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>
                  {state.machine && t('fn.machineTranslated', 'Parts of this page were translated automatically and may contain mistakes.')}
                  {state.machine && state.fallback && ' '}
                  {state.fallback && t('fn.originalLanguageShown', 'Some details are shown in the original language because a translation is not available yet.')}
                </span>
              </p>
            )}

            <section className="mt-10 border-t border-line pt-8 print:mt-6 print:pt-4" aria-labelledby="function-about">
              <h2 id="function-about" className="micro text-secondary">{t('fn.aboutTitle')}</h2>
              <p lang={locale} className="mt-4 max-w-3xl text-lg leading-8 text-foreground wrap-anywhere">{functionDescription ?? '—'}</p>
            </section>

            <ServiceInstructionsView record={item.data} />
            <ServiceHistory serviceId={item.data.id} />
            <FeedbackPrompt serviceId={item.data.id} onReport={() => reportButtonRef.current?.querySelector('button')?.click()} />
          </Reveal>

          <Reveal as="aside" index={1} className="space-y-6 lg:sticky lg:top-28 print:mt-8 print:space-y-4">
            <Card>
              <CardHeader title={t('field.organization')} />
              <CardBody className="space-y-5">
                <dl className="space-y-5">
                  <div>
                    <dt className="micro text-secondary">{t('field.organization')}</dt>
                    <dd className="mt-1.5 text-base font-medium text-foreground wrap-anywhere">
                      {organization ? (
                        <Link lang={locale} to={`/organizations/${organization.id}`} className="rounded-sm underline-offset-4 fine:hover:underline">
                          {organizationName}
                        </Link>
                      ) : (
                        t('fn.organizationUnknown', 'Organization unknown')
                      )}
                    </dd>
                  </div>
                  {item.data.category && (
                    <div>
                      <dt className="micro text-secondary">{t('field.category')}</dt>
                      <dd className="mt-1.5"><Badge tone="accent" size="sm">{item.data.category}</Badge></dd>
                    </div>
                  )}
                  <div className="print:hidden">
                    <dt className="micro text-secondary">{t('field.id')}</dt>
                    <dd className="mt-1.5 text-base font-medium tabular-nums text-foreground">{item.data.id}</dd>
                  </div>
                </dl>
                {organization && hasContactDetails(organization.contact) && (
                  <div className="border-t border-line pt-5">
                    <OrganizationContactDetails contact={organization.contact} onLinkOpen={(type) => track({ type, serviceId: item.data?.id })} />
                  </div>
                )}
              </CardBody>
            </Card>
            <Card>
              <CardHeader title={t('verification.title', 'Information check')} />
              <CardBody>
                <VerificationNotice info={item.data} onSourceOpen={() => track({ type: 'OFFICIAL_LINK_CLICK', serviceId: item.data?.id })} />
              </CardBody>
            </Card>
            <p className="border-s-2 border-line ps-4 text-sm leading-6 text-secondary">{t('app.demoNotice')}</p>
          </Reveal>
        </div>
      ) : null}
    </div>
  );
}
