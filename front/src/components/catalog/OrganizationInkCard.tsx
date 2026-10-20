import { InkCard, InkCardArrow } from '../ui/Card';
import { Badge } from '../ui/Badge';
import type { PublicOrganization } from '../../types/api';
import { organizationInitials, truncate } from '../../utils/format';
import { useI18n } from '../../contexts/i18n';
import { localizedText } from '../../utils/translations';

interface OrganizationInkCardProps {
  organization: PublicOrganization;
  functionCount: number;
}

export function OrganizationInkCard({ organization, functionCount }: OrganizationInkCardProps) {
  const { locale, t } = useI18n();
  const name = localizedText(organization.name, organization.nameTranslations, locale);
  const description = localizedText(organization.description, organization.descriptionTranslations, locale);

  return (
    <InkCard to={`/organizations/${organization.id}`} className="flex h-full min-h-[16rem] flex-col p-6 sm:p-7">
      <div className="flex items-start justify-between gap-4">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-pill border border-ink-line text-sm font-semibold uppercase tracking-wide" aria-hidden="true">
          {organizationInitials(name)}
        </span>
        <InkCardArrow />
      </div>
      <h3 lang={locale} className="mt-8 text-2xl font-semibold leading-tight wrap-anywhere">{name}</h3>
      {description && <p lang={locale} className="mt-3 flex-1 text-sm leading-6 text-ink-secondary wrap-anywhere">{truncate(description, 130)}</p>}
      <div className="mt-6">
        <Badge tone="onInk" size="sm">{t('catalog.servicesCount', { count: functionCount })}</Badge>
      </div>
    </InkCard>
  );
}
