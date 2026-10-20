import { DownloadIcon } from 'lucide-react';
import { useI18n } from '../../contexts/i18n';
import type { DocumentDetail } from '../../types/api';
import { documentFormLabels, formLinkOf } from '../../utils/documentDetails';

/**
 * What the organization said about one required document. Nothing is assumed: missing parts are named as "not
 * provided by the organization", and a form link appears only when the organization published an approved form.
 */
export function DocumentDetailNote({ detail, language, compact = false }: { detail: DocumentDetail | undefined; language?: string; compact?: boolean }) {
  const { t } = useI18n();
  const formLink = formLinkOf(detail);
  if (!detail) {
    return (
      <p className="text-sm leading-6 text-secondary">
        {t('documents.detailsNotProvided', 'Original or copy and number of copies: not provided by the organization')}
      </p>
    );
  }
  const facts = [
    detail.form ? t(`documents.form.${detail.form}`, documentFormLabels[detail.form])
      : t('documents.formNotProvided', 'Original or copy: not provided by the organization'),
    detail.copies ? `${t('documents.copies', 'Copies')}: ${detail.copies}` : null,
  ].filter(Boolean);
  return (
    <div className="space-y-1 text-sm leading-6 text-secondary">
      <p className="text-foreground">{facts.join(' · ')}</p>
      {detail.validity && <p lang={language}>{t('documents.validity', 'Validity')}: {detail.validity}</p>}
      {detail.notes && <p lang={language} className="wrap-anywhere">{detail.notes}</p>}
      {formLink && (
        compact ? (
          <p className="wrap-anywhere">{t('documents.formLink', 'Approved form')}: {formLink}</p>
        ) : (
          <a href={formLink} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 font-medium text-accent-text underline-offset-4 fine:hover:underline print:hidden">
            <DownloadIcon className="h-3.5 w-3.5" aria-hidden="true" />
            {t('documents.downloadForm', 'Download the approved form')}
            <span className="sr-only"> ({t('a11y.opensInNewTab', 'opens in a new tab')})</span>
          </a>
        )
      )}
    </div>
  );
}
