import { useState } from 'react';
import { LanguagesIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Checkbox } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { errorMessage } from '../../utils/errors';
import { useBackfillTranslations, useTranslationCapabilities } from './queries';

/**
 * Queues the caller's services that miss translations in an active language. Published services are never changed in
 * place: with the option checked they get a draft revision that is machine-translated and then reviewed as usual.
 */
export function TranslationBackfillButton() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const allowed = hasPermission('FUNCTIONS_EDIT') && hasPermission('FUNCTIONS_TRANSLATIONS_EDIT');
  const capabilities = useTranslationCapabilities();
  const backfill = useBackfillTranslations();
  const [open, setOpen] = useState(false);
  const [includePublished, setIncludePublished] = useState(false);

  if (!allowed || !capabilities.data?.available) return null;

  const submit = () =>
    backfill.mutate(includePublished, {
      onSuccess: (result) => {
        setOpen(false);
        toast.success(t('fnAdmin.backfillDone', {
          queued: result.queued + result.revisionsQueued,
          revisions: result.revisionsCreated,
          skipped: result.skippedUnderReview,
        }));
        if (result.limitReached) toast.info(t('fnAdmin.backfillMore'));
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    });

  return (
    <>
      <Button variant="outline" size="sm" icon={<LanguagesIcon />} onClick={() => setOpen(true)}>
        {t('fnAdmin.backfill')}
      </Button>
      <Modal
        open={open}
        size="sm"
        title={t('fnAdmin.backfill')}
        onClose={() => setOpen(false)}
        closeDisabled={backfill.isPending}
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={backfill.isPending}>{t('action.cancel')}</Button>
            <Button variant="dark" onClick={submit} loading={backfill.isPending}>{t('fnAdmin.backfillConfirm')}</Button>
          </>
        }
      >
        <p className="text-sm leading-6 text-secondary">{t('fnAdmin.backfillHint')}</p>
        <label className="mt-4 flex items-start gap-3 text-sm text-foreground">
          <Checkbox className="mt-0.5" checked={includePublished} onChange={(event) => setIncludePublished(event.target.checked)} />
          <span>
            <span className="block font-medium">{t('fnAdmin.backfillPublished')}</span>
            <span className="mt-0.5 block text-xs leading-5 text-secondary">{t('fnAdmin.backfillPublishedHint')}</span>
          </span>
        </label>
      </Modal>
    </>
  );
}
