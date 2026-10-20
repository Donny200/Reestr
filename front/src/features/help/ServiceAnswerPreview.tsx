import { BookOpenCheckIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { OrganizationContactDetails } from '../../components/catalog/OrganizationContactDetails';
import { useI18n } from '../../contexts/i18n';
import type { CatalogFunction, PublicOrganization } from '../../types/api';
import { hasContactDetails } from '../../utils/contact';
import { requirementLines } from '../../utils/format';
import { instructionItems, localizedInstruction } from '../../utils/instructions';
import { localizedText } from '../../utils/translations';

interface ServiceAnswerPreviewProps {
  service: CatalogFunction;
  organization: PublicOrganization | undefined;
}

/**
 * "Before you ask": only what the published service card already says (documents, steps, time, fee, where to apply,
 * contacts). Nothing is generated or guessed; empty fields are simply not shown.
 */
export function ServiceAnswerPreview({ service, organization }: ServiceAnswerPreviewProps) {
  const { t, locale } = useI18n();
  const value = (field: Parameters<typeof localizedInstruction>[1]) => localizedInstruction(service, field, locale);
  const documents = value('requiredDocuments');
  const steps = value('steps');
  const facts = [
    { label: t('instructions.processingTime', 'Processing time'), value: value('processingTime')?.text },
    { label: t('instructions.fee', 'Official fee'), value: value('fee')?.text },
    { label: t('instructions.whereHowToApply', 'Where and how to apply'), value: value('whereHowToApply')?.text },
  ].filter((fact) => fact.value);
  const documentItems = documents ? instructionItems(documents.text) : requirementLines(service.requirements);
  const stepItems = steps ? instructionItems(steps.text) : [];
  const contact = organization && hasContactDetails(organization.contact) ? organization.contact : null;
  const name = localizedText(service.name, service.nameTranslations, locale);
  if (documentItems.length === 0 && stepItems.length === 0 && facts.length === 0 && !contact) return null;

  return (
    <section aria-labelledby="help-preview-title" className="rounded-card-sm border border-line p-5">
      <h2 id="help-preview-title" className="flex items-center gap-2 text-base font-semibold text-foreground">
        <BookOpenCheckIcon className="h-4 w-4 shrink-0" aria-hidden="true" />
        {t('support.previewTitle', 'Before you ask: what the service page says')}
      </h2>
      <p className="mt-1 text-sm leading-6 text-secondary">
        {t('support.previewText', 'This is the information the organization has published for this service. If it answers your question, you do not need to send it.')}{' '}
        <Link to={`/functions/${service.id}`} className="font-medium text-accent-text underline-offset-4 fine:hover:underline" lang={locale}>{name}</Link>
      </p>
      <div className="mt-4 grid gap-5 md:grid-cols-2" lang={locale}>
        {documentItems.length > 0 && (
          <div>
            <h3 className="micro text-secondary">{documents ? t('instructions.requiredDocuments', 'Required documents') : t('field.requirements', 'Requirements')}</h3>
            <ul className="mt-2 list-disc space-y-1 ps-5 text-sm leading-6 text-foreground">
              {documentItems.map((item, index) => <li key={index} className="wrap-anywhere">{item}</li>)}
            </ul>
          </div>
        )}
        {stepItems.length > 0 && (
          <div>
            <h3 className="micro text-secondary">{t('instructions.steps', 'Step-by-step procedure')}</h3>
            <ol className="mt-2 list-decimal space-y-1 ps-5 text-sm leading-6 text-foreground">
              {stepItems.map((item, index) => <li key={index} className="wrap-anywhere">{item}</li>)}
            </ol>
          </div>
        )}
        {facts.length > 0 && (
          <dl className="space-y-3">
            {facts.map((fact) => (
              <div key={fact.label}>
                <dt className="micro text-secondary">{fact.label}</dt>
                <dd className="mt-1 whitespace-pre-line text-sm leading-6 text-foreground wrap-anywhere">{fact.value}</dd>
              </div>
            ))}
          </dl>
        )}
        {contact && (
          <div>
            <h3 className="micro mb-2 text-secondary">{t('guide.contacts', 'Contacts of the organization')}</h3>
            <OrganizationContactDetails contact={contact} />
          </div>
        )}
      </div>
    </section>
  );
}
