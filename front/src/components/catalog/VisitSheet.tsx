import { useI18n } from '../../contexts/i18n';
import type { CatalogFunction, PublicOrganization } from '../../types/api';
import type { VisitorAppointment } from '../../types/appointments';
import { appointmentStatusText, zonedDate } from '../../features/appointments/format';
import { detailFor, documentFormLabels, formLinkOf, hasAnyDetail } from '../../utils/documentDetails';
import { formatDate, requirementLines } from '../../utils/format';
import { instructionItems, localizedInstruction } from '../../utils/instructions';
import { printedPageUrl } from '../../utils/publicSite';
import { localizedText } from '../../utils/translations';

interface VisitSheetProps {
  record: CatalogFunction;
  organization?: PublicOrganization;
  appointment?: VisitorAppointment | null;
  /** Canonical public address configured on the server; the page's own origin is never printed. */
  publicBaseUrl: string | null | undefined;
}

/** An empty square to tick by hand; required items are never shown as already done on paper. */
function Box() {
  return <span className="mt-[0.3em] inline-block h-[0.9em] w-[0.9em] shrink-0 border border-black" aria-hidden="true" />;
}

/**
 * Printable preparation sheet for a visit (browser print / save as PDF). It is hidden on screen and replaces the page
 * when printing. Only information that exists is printed; it is a guide, not an application or a certificate.
 */
export function VisitSheet({ record, organization, appointment, publicBaseUrl }: VisitSheetProps) {
  const { t, locale } = useI18n();
  const name = localizedText(record.name, record.nameTranslations, locale);
  const organizationName = organization ? localizedText(organization.name, organization.nameTranslations, locale)
    : appointment?.organizationName ?? null;
  const description = localizedText(record.description, record.descriptionTranslations, locale);
  const value = (field: Parameters<typeof localizedInstruction>[1]) => localizedInstruction(record, field, locale);
  const whoCanUse = value('whoCanUse');
  const steps = value('steps');
  const documents = value('requiredDocuments');
  const whereHow = value('whereHowToApply');
  const fee = value('fee');
  const time = value('processingTime');
  const sourceDocuments = instructionItems(record.instructions?.requiredDocuments);
  const shownDocuments = documents ? instructionItems(documents.text) : [];
  const requirements = requirementLines(record.requirements);
  const contact = organization?.contact ?? appointment?.organizationContact ?? null;
  const pageUrl = printedPageUrl(publicBaseUrl, `/functions/${record.id}`);
  const anyDetails = hasAnyDetail(record.documentDetails);

  const detailText = (index: number) => {
    const detail = detailFor(record.documentDetails, sourceDocuments.length === shownDocuments.length ? sourceDocuments[index] : shownDocuments[index]);
    if (!detail) return anyDetails ? t('documents.detailsNotProvided', 'Original or copy and number of copies: not provided by the organization') : null;
    const parts = [
      detail.form ? t(`documents.form.${detail.form}`, documentFormLabels[detail.form]) : t('documents.formNotProvided', 'Original or copy: not provided by the organization'),
      detail.copies ? `${t('documents.copies', 'Copies')}: ${detail.copies}` : null,
      detail.validity ? `${t('documents.validity', 'Validity')}: ${detail.validity}` : null,
      detail.notes,
      formLinkOf(detail) ? `${t('documents.formLink', 'Approved form')}: ${formLinkOf(detail)}` : null,
    ].filter(Boolean);
    return parts.join(' · ');
  };

  return (
    <article className="visit-sheet hidden text-black print:block" lang={locale} aria-label={t('visitSheet.title', 'Visit preparation sheet')}>
      <header className="flex items-start justify-between gap-6 border-b border-black pb-3 text-[9.5pt] leading-snug">
        <div>
          <p className="font-semibold">{t('app.name')}</p>
          {pageUrl && <p className="break-all" data-testid="visit-sheet-url">{pageUrl}</p>}
        </div>
        <div className="text-end">
          <p>{t('visitSheet.printedOn', 'Printed on')} {formatDate(new Date().toISOString(), locale)}</p>
          {record.lastVerifiedAt && <p>{t('visitSheet.lastReviewed', 'Last reviewed by the organization')}: {formatDate(record.lastVerifiedAt, locale)}</p>}
          {record.lastUpdatedAt && <p>{t('history.lastUpdated', 'Last updated')}: {formatDate(record.lastUpdatedAt, locale)}</p>}
        </div>
      </header>

      <p className="mt-4 text-[9pt] uppercase tracking-wide">{t('visitSheet.title', 'Visit preparation sheet')}</p>
      <h1 className="mt-1 text-[18pt] font-semibold leading-tight">{name}</h1>
      {organizationName && <p className="mt-1 text-[12pt]">{organizationName}</p>}
      {description && <p className="mt-2 text-[10.5pt] leading-snug">{description}</p>}

      {appointment && (
        <section className="mt-4 break-inside-avoid border-2 border-black p-3" aria-label={t('visitSheet.appointment', 'Your appointment')}>
          <h2 className="text-[12pt] font-semibold">{t('visitSheet.appointment', 'Your appointment')}</h2>
          <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-[10.5pt]">
            <dt className="font-semibold">{t('appointments.date', 'Date')}</dt>
            <dd>{zonedDate(appointment.startsAt, appointment.timeZone, locale)}</dd>
            <dt className="font-semibold">{t('appointments.time', 'Time')}</dt>
            <dd>{appointment.localStart}–{appointment.localEnd} ({appointment.timeZone})</dd>
            {contact?.address && (<><dt className="font-semibold">{t('contact.address', 'Address')}</dt><dd className="whitespace-pre-line">{contact.address}</dd></>)}
            <dt className="font-semibold">{t('appointments.reference', 'Booking reference')}</dt>
            <dd className="font-mono text-[12pt] tracking-wider">{appointment.reference}</dd>
            <dt className="font-semibold">{t('appointments.statusLabel', 'Status')}</dt>
            <dd>{appointmentStatusText(appointment, t)}</dd>
          </dl>
        </section>
      )}

      {(whoCanUse || fee || time) && (
        <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-[10.5pt]">
          {whoCanUse && (<><dt className="font-semibold">{t('instructions.whoCanUse', 'Who can use this service')}</dt><dd className="whitespace-pre-line">{whoCanUse.text}</dd></>)}
          {fee && (<><dt className="font-semibold">{t('instructions.fee', 'Official fee')}</dt><dd>{fee.text}</dd></>)}
          {time && (<><dt className="font-semibold">{t('instructions.processingTime', 'Processing time')}</dt><dd>{time.text}</dd></>)}
        </dl>
      )}

      <section className="mt-5" aria-label={t('visitSheet.checklist', 'Bring with you')}>
        <h2 className="border-b border-black pb-1 text-[12pt] font-semibold">{t('visitSheet.checklist', 'Bring with you')}</h2>
        {shownDocuments.length > 0 || requirements.length > 0 ? (
          <ul className="mt-2 space-y-2 text-[10.5pt]" data-testid="visit-sheet-checklist">
            {shownDocuments.map((item, index) => (
              <li key={`document-${index}`} className="flex break-inside-avoid items-start gap-2.5">
                <Box />
                <span>
                  <span className="font-medium">{item}</span>
                  {detailText(index) && <span className="block text-[9.5pt]">{detailText(index)}</span>}
                </span>
              </li>
            ))}
            {requirements.map((item, index) => (
              <li key={`requirement-${index}`} className="flex break-inside-avoid items-start gap-2.5"><Box /><span>{item}</span></li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-[10.5pt]">{t('visitSheet.noDocuments', 'The organization has not published a document list. Contact the organization before your visit.')}</p>
        )}
      </section>

      {steps && (
        <section className="mt-5 break-inside-avoid-page">
          <h2 className="border-b border-black pb-1 text-[12pt] font-semibold">{t('instructions.steps', 'Step-by-step procedure')}</h2>
          <ol className="mt-2 list-decimal space-y-1 ps-6 text-[10.5pt]">
            {instructionItems(steps.text).map((step, index) => <li key={index}>{step}</li>)}
          </ol>
        </section>
      )}

      {whereHow && (
        <section className="mt-5 break-inside-avoid">
          <h2 className="border-b border-black pb-1 text-[12pt] font-semibold">{t('instructions.whereHowToApply', 'Where and how to apply')}</h2>
          <p className="mt-2 whitespace-pre-line text-[10.5pt]">{whereHow.text}</p>
        </section>
      )}

      {contact && (contact.address || contact.phone || contact.workingHours) && (
        <section className="mt-5 break-inside-avoid">
          <h2 className="border-b border-black pb-1 text-[12pt] font-semibold">{t('guide.contacts', 'Contacts of the organization')}</h2>
          <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-[10.5pt]">
            {contact.address && (<><dt className="font-semibold">{t('contact.address', 'Address')}</dt><dd className="whitespace-pre-line">{contact.address}</dd></>)}
            {contact.phone && (<><dt className="font-semibold">{t('contact.phone', 'Phone')}</dt><dd>{contact.phone}</dd></>)}
            {contact.workingHours && (<><dt className="font-semibold">{t('contact.workingHours', 'Working hours')}</dt><dd className="whitespace-pre-line">{contact.workingHours}</dd></>)}
          </dl>
        </section>
      )}

      <footer className="mt-6 border-t border-black pt-2 text-[8.5pt] leading-snug">
        <p>{t('visitSheet.disclaimer', 'This sheet is a preparation guide based on the information published on this site. It is not an application, an official certificate or proof that you are eligible. Confirm details with the organization before your visit.')}</p>
      </footer>
    </article>
  );
}
