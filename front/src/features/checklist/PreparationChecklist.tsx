import { useId, useState, type ReactNode } from 'react';
import { ClipboardCheckIcon, Trash2Icon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Checkbox } from '../../components/ui/Input';
import { ConfirmModal } from '../../components/ui/Modal';
import { useI18n } from '../../contexts/i18n';
import type { CatalogFunction } from '../../types/api';
import { cn } from '../../lib/cn';
import { useChecklist } from './useChecklist';
import type { ChecklistItem, ChecklistSection } from './checklistStore';

interface PreparationChecklistProps {
  record: CatalogFunction;
  /** Which items to render in this block; the progress header is shared through the hook. */
  section: ChecklistSection;
  label: string;
  numbered?: boolean;
  /** Extra information under an item (structured document details). */
  renderDetail?: (item: ChecklistItem) => ReactNode;
}

export function ChecklistItems({ record, section, label, numbered = false, renderDetail }: PreparationChecklistProps) {
  const { t, locale } = useI18n();
  const prefix = useId();
  const { items, isDone, toggle } = useChecklist(record, locale);
  const own = items.filter((item) => item.section === section);
  if (own.length === 0) return null;
  const List = numbered ? 'ol' : 'ul';
  return (
    <List className={cn('space-y-2', numbered && 'list-none')} aria-label={label}>
      {own.map((item, index) => {
        const done = isDone(item);
        const id = `${prefix}-${index}`;
        return (
          <li key={item.key} className="break-inside-avoid">
            <label htmlFor={id} className="flex cursor-pointer items-start gap-3 rounded-control px-1 py-1.5 text-base leading-7 text-foreground fine:hover:bg-surface">
              <Checkbox id={id} className="mt-1" checked={done} onChange={() => toggle(item)} />
              {numbered && (
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-pill bg-ink text-sm font-semibold tabular-nums text-ink-fg print:border print:border-line print:bg-transparent print:text-foreground" aria-hidden="true">
                  {index + 1}
                </span>
              )}
              <span className={cn('min-w-0 wrap-anywhere', done && 'text-secondary line-through decoration-line')}>{item.text}</span>
              {done && <span className="sr-only"> ({t('checklist.done', 'done')})</span>}
            </label>
            {renderDetail && <div className="ps-10 pb-1">{renderDetail(item)}</div>}
          </li>
        );
      })}
    </List>
  );
}

/** Progress, stale-item notice, clear action and the "preparation aid" disclaimer for the whole page. */
export function ChecklistSummary({ record }: { record: CatalogFunction }) {
  const { t, locale } = useI18n();
  const { items, stale, doneCount, removeStale, clear, persisted } = useChecklist(record, locale);
  const [confirm, setConfirm] = useState(false);
  if (items.length === 0 && stale.length === 0) return null;
  const clearAll = () => {
    clear();
    setConfirm(false);
    toast.success(t('checklist.cleared', 'Checklist cleared'));
  };
  return (
    <section className="mt-10 rounded-card-sm border border-line p-4 print:mt-6 sm:p-5" aria-labelledby="checklist-summary">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 id="checklist-summary" className="flex items-center gap-2 text-base font-semibold text-foreground">
            <ClipboardCheckIcon className="h-4 w-4 shrink-0 print:hidden" aria-hidden="true" />
            {t('checklist.title', 'Your preparation checklist')}
          </h2>
          <p className="mt-1 text-sm leading-6 text-secondary" aria-live="polite">
            <span className="font-medium tabular-nums text-foreground">{doneCount} / {items.length}</span> {t('checklist.progress', 'items ticked. Tick documents and steps as you prepare them.')}
          </p>
          <p className="mt-2 text-sm leading-6 text-secondary">
            {t('checklist.privacy', 'Progress is saved only in this browser and is never sent to the server. Do not enter personal details anywhere on this page.')}
          </p>
          <p className="mt-1 text-sm font-medium leading-6 text-foreground">
            {t('checklist.disclaimer', 'This checklist is a preparation aid. It is not an application and not an official confirmation that you are eligible.')}
          </p>
          {!persisted && <p className="mt-1 text-sm text-status-pending">{t('checklist.sessionOnly', 'Saved for this visit only: this browser blocks local storage.')}</p>}
        </div>
        {(doneCount > 0 || stale.length > 0) && (
          <Button variant="outline" size="sm" icon={<Trash2Icon />} onClick={() => setConfirm(true)} className="print:hidden">
            {t('checklist.clear', 'Clear checklist')}
          </Button>
        )}
      </div>
      {stale.length > 0 && (
        <div className="mt-4 rounded-control bg-status-pending-bg px-4 py-3 text-sm leading-6 text-status-pending" role="status">
          <p className="font-medium">{t('checklist.staleTitle', 'The organization changed this information. These ticked items are no longer part of the current list:')}</p>
          <ul className="mt-1 list-disc ps-5">
            {stale.map((item) => <li key={item.key} className="wrap-anywhere">{item.text}</li>)}
          </ul>
          <Button variant="ghost" size="sm" className="mt-2" onClick={removeStale}>{t('checklist.removeStale', 'Remove outdated items')}</Button>
        </div>
      )}
      <ConfirmModal
        open={confirm}
        title={t('checklist.clear', 'Clear checklist')}
        message={t('checklist.clearConfirm', 'Remove every ticked item for this service from this browser?')}
        confirmLabel={t('checklist.clear', 'Clear checklist')}
        cancelLabel={t('action.cancel', 'Cancel')}
        onConfirm={clearAll}
        onClose={() => setConfirm(false)}
      />
    </section>
  );
}

export type { ChecklistItem };
