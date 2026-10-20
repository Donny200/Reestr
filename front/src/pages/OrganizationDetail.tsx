import { useParams } from 'react-router-dom';
import { BackLink } from '../components/catalog/BackLink';
import { FunctionListRow } from '../components/catalog/FunctionListRow';
import { OrganizationContactDetails } from '../components/catalog/OrganizationContactDetails';
import { VerificationNotice } from '../components/catalog/VerificationNotice';
import { SectionHeading } from '../components/home/SectionHeading';
import { StatusPill } from '../components/ui/Badge';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Eyebrow } from '../components/ui/Eyebrow';
import { Reveal } from '../components/ui/Reveal';
import { SkeletonList, SkeletonText } from '../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { usePublicOrganization } from '../features/organizations/queries';
import { usePublicFunctions } from '../features/functions/queries';
import { useEngagementTracker, usePageView } from '../features/engagement/tracking';
import { ReportProblemButton } from '../features/reports/ReportProblemButton';
import { organizationInitials } from '../utils/format';
import { localizedText } from '../utils/translations';

export function OrganizationDetail() {
  const { id } = useParams<{ id: string }>();
  const organizationId = Number(id);
  const { t, locale } = useI18n();

  const organization = usePublicOrganization(organizationId);
  const functions = usePublicFunctions({ organizationId });
  const track = useEngagementTracker();
  const trackedOrganization = organization.data?.id;
  usePageView(trackedOrganization === undefined ? null : 'CATALOG_VIEW', undefined, trackedOrganization);
  const organizationName = localizedText(organization.data?.name, organization.data?.nameTranslations, locale);
  const organizationDescription = localizedText(organization.data?.description, organization.data?.descriptionTranslations, locale);
  const functionList = functions.data ?? [];

  return (
    <div className="shell py-8 sm:py-12 lg:py-16">
      <BackLink to="/#organizations" label={t('nav.organizations')} />

      <Reveal className="mt-6">
        <Card size="lg" className="p-6 sm:p-10">
          {organization.isPending ? (
            <SkeletonText lines={4} />
          ) : organization.error ? (
            <ErrorState error={organization.error} onRetry={() => void organization.refetch()} />
          ) : organization.data ? (
            <div>
              <div className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex min-w-0 items-start gap-5">
                  <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-pill bg-ink text-base font-semibold uppercase tracking-wide text-ink-fg" aria-hidden="true">
                    {organizationInitials(organizationName ?? organization.data.name)}
                  </span>
                  <div className="min-w-0 pt-1">
                    <Eyebrow>{t('field.organization')}</Eyebrow>
                    <h1 lang={locale} className="mt-3 text-4xl font-semibold text-foreground wrap-anywhere sm:text-5xl">{organizationName}</h1>
                  </div>
                </div>
                <StatusPill tone="published" className="w-fit shrink-0">{t('status.active')}</StatusPill>
              </div>
              <div className="mt-8 border-t border-line pt-7">
                <p lang={locale} className="max-w-3xl text-lg leading-8 text-foreground wrap-anywhere">{organizationDescription ?? '—'}</p>
              </div>
              <div className="mt-6 flex flex-wrap gap-2">
                <ReportProblemButton entityType="ORGANIZATION" entityId={organization.data.id} />
              </div>
            </div>
          ) : null}
        </Card>
      </Reveal>

      {organization.data && (
        <Reveal className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.65fr)_minmax(18rem,0.85fr)] lg:items-start">
          <Card>
            <CardHeader title={t('contact.title', 'Contact and visiting information')} />
            <CardBody>
              <OrganizationContactDetails contact={organization.data.contact} onLinkOpen={(type) => track({ type, organizationId: trackedOrganization })} />
            </CardBody>
          </Card>
          <Card>
            <CardHeader title={t('verification.title', 'Information check')} />
            <CardBody>
              <VerificationNotice info={organization.data} onSourceOpen={() => track({ type: 'OFFICIAL_LINK_CLICK', organizationId: trackedOrganization })} />
            </CardBody>
          </Card>
        </Reveal>
      )}

      <section className="mt-14">
        <Reveal>
          <SectionHeading
            eyebrow={t('nav.functions')}
            title={t('org.functionsTitle')}
            aside={
              !functions.isPending && !functions.error ? (
                <p className="inline-flex min-h-10 w-fit items-center rounded-pill border border-line px-4 text-sm font-medium tabular-nums text-secondary" aria-live="polite">
                  {functionList.length} {t('home.resultsCount')}
                </p>
              ) : undefined
            }
          />
        </Reveal>
        <div className="mt-8">
          {functions.isPending ? (
            <SkeletonList count={3} />
          ) : functions.error ? (
            <Card><ErrorState error={functions.error} onRetry={() => void functions.refetch()} /></Card>
          ) : functionList.length === 0 ? (
            <Card><EmptyState title={t('state.emptyTitle')} description={t('org.emptyFunctions')} /></Card>
          ) : (
            <ol className="border-b border-line">
              {functionList.map((item, index) => (
                <FunctionListRow key={item.id} item={item} index={index + 1} organizationName={organizationName ?? undefined} />
              ))}
            </ol>
          )}
        </div>
      </section>
    </div>
  );
}
