import type { ReactNode } from 'react';
import { ClockIcon, ExternalLinkIcon, MapIcon, MapPinIcon, PhoneIcon } from 'lucide-react';
import { useI18n } from '../../contexts/i18n';
import { useRegions } from '../../features/reference/queries';
import type { OrganizationContact } from '../../types/api';
import { coordinatesOf, openStreetMapUrl, telephoneHref } from '../../utils/contact';

function Row({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3">
      <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-pill bg-surface text-foreground [&_svg]:h-4 [&_svg]:w-4 print:hidden" aria-hidden="true">
        {icon}
      </span>
      <div className="min-w-0">
        <dt className="micro text-secondary">{label}</dt>
        <dd className="mt-1 text-base leading-7 text-foreground wrap-anywhere">{children}</dd>
      </div>
    </div>
  );
}

function ExternalLink({ href, children, onOpen }: { href: string; children: ReactNode; onOpen?: () => void }) {
  const { t } = useI18n();
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" onClick={onOpen} onAuxClick={onOpen} className="inline-flex items-center gap-1.5 rounded-sm font-medium text-accent-text underline-offset-4 fine:hover:underline">
      {children}
      <ExternalLinkIcon className="h-3.5 w-3.5 shrink-0 print:hidden" aria-hidden="true" />
      <span className="sr-only"> ({t('a11y.opensInNewTab', 'opens in a new tab')})</span>
    </a>
  );
}

interface OrganizationContactDetailsProps {
  contact: OrganizationContact | null | undefined;
  onLinkOpen?: (type: 'PHONE_CLICK' | 'MAP_CLICK') => void;
}

export function OrganizationContactDetails({ contact, onLinkOpen }: OrganizationContactDetailsProps) {
  const { t } = useI18n();
  const regions = useRegions();
  const notProvided = <span className="text-secondary">{t('contact.notProvided', 'Not provided yet')}</span>;
  const region = contact?.regionCode ? regions.data?.find((item) => item.code === contact.regionCode)?.name : undefined;
  const coordinates = coordinatesOf(contact);

  return (
    <dl className="space-y-5">
      <Row icon={<MapPinIcon />} label={t('contact.address', 'Address')}>
        {contact?.address ? (
          <>
            <span className="whitespace-pre-line">{contact.address}</span>
            {region && <span className="block text-sm text-secondary">{region}</span>}
          </>
        ) : (
          notProvided
        )}
      </Row>
      <Row icon={<PhoneIcon />} label={t('contact.phone', 'Phone')}>
        {contact?.phone ? (
          <a href={telephoneHref(contact.phone)} onClick={() => onLinkOpen?.('PHONE_CLICK')} className="rounded-sm font-medium tabular-nums underline-offset-4 fine:hover:underline">
            {contact.phone}
          </a>
        ) : (
          notProvided
        )}
      </Row>
      <Row icon={<ClockIcon />} label={t('contact.workingHours', 'Working hours')}>
        {contact?.workingHours ? <span className="whitespace-pre-line">{contact.workingHours}</span> : notProvided}
      </Row>
      {(coordinates || contact?.mapUrl) && (
        <Row icon={<MapIcon />} label={t('contact.map', 'Map')}>
          <span className="flex flex-col gap-1">
            {coordinates && (
              <ExternalLink href={openStreetMapUrl(coordinates)} onOpen={() => onLinkOpen?.('MAP_CLICK')}>{t('contact.openStreetMap', 'Open in OpenStreetMap')}</ExternalLink>
            )}
            {contact?.mapUrl && <ExternalLink href={contact.mapUrl} onOpen={() => onLinkOpen?.('MAP_CLICK')}>{t('contact.mapLink', 'Open the map link')}</ExternalLink>}
            {coordinates && (
              <span className="text-xs tabular-nums text-secondary">{coordinates.latitude}, {coordinates.longitude}</span>
            )}
          </span>
        </Row>
      )}
    </dl>
  );
}
