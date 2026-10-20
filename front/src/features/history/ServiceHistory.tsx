import { HistoryIcon } from 'lucide-react';
import { useI18n } from '../../contexts/i18n';
import { usePublicFunctionHistory } from '../functions/queries';
import { formatDate } from '../../utils/format';
import { changeTypeLabel, sectionLabel } from './historyLabels';

/** Concise public history: when a reviewed version went live and which sections changed. Nothing about drafts or staff. */
export function ServiceHistory({ serviceId }: { serviceId: number }) {
  const { t, locale } = useI18n();
  const history = usePublicFunctionHistory(serviceId);
  const entries = history.data ?? [];
  if (history.isPending || history.error || entries.length === 0) return null;
  return (
    <section className="mt-10 border-t border-line pt-8 print:mt-6 print:pt-4" aria-labelledby="service-history">
      <h2 id="service-history" className="micro flex items-center gap-2 text-secondary">
        <HistoryIcon className="h-4 w-4 print:hidden" aria-hidden="true" />
        {t('history.title', 'What changed')}
      </h2>
      <p className="mt-2 text-sm leading-6 text-secondary">{t('history.subtitle', 'Only reviewed and published changes are listed. Organization contact details are maintained separately and are not part of this history.')}</p>
      <ol className="mt-4 space-y-3" aria-label={t('history.title', 'What changed')}>
        {entries.map((change) => (
          <li key={`${change.publishedAt}-${change.changeType}`} className="flex flex-col gap-1 rounded-card-sm border border-line p-3 break-inside-avoid sm:flex-row sm:items-start sm:gap-4">
            <time dateTime={change.publishedAt} className="shrink-0 text-sm tabular-nums text-secondary sm:w-28">{formatDate(change.publishedAt, locale)}</time>
            <div className="min-w-0">
              <p className="text-sm font-medium text-foreground">{changeTypeLabel(change, t)}</p>
              {change.sections.length > 0 && (
                <ul className="mt-1.5 flex flex-wrap gap-1.5" aria-label={t('history.sections', 'Changed sections')}>
                  {change.sections.map((section) => (
                    <li key={section} className="rounded-pill border border-line px-2.5 py-0.5 text-xs font-medium text-foreground">{sectionLabel(section, t)}</li>
                  ))}
                </ul>
              )}
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
