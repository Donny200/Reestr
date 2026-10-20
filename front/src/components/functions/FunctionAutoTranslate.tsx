import { useCallback, useRef, useState } from 'react';
import { toast } from 'sonner';
import { RefreshCwIcon, RotateCcwIcon, SparklesIcon } from 'lucide-react';
import { useI18n } from '../../contexts/i18n';
import { useAutoTranslateFunction, useRequeueTranslation, useTranslationCapabilities } from '../../features/functions/queries';
import { FunctionErrorState } from '../../features/functions/FunctionErrorState';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { hasFunctionTranslation } from '../../utils/functionLocalization';
import { errorMessage, type Translate } from '../../utils/errors';
import { formatDateTime } from '../../utils/format';
import { Button } from '../ui/Button';
import { SkeletonText } from '../ui/Skeleton';
import { isEditableStatus, type AdminFunction, type MachineTranslationSummary } from '../../types/adminFunctions';

interface Props {
  record: AdminFunction;
  language: string;
  blocked: boolean;
  busy: boolean;
}

interface Attempt {
  languages: string[];
  overwriteMachine: boolean;
}

const STATUS_LABELS = {
  PENDING: ['fnAdmin.autoQueued', 'Queued for automatic translation'],
  RUNNING: ['fnAdmin.autoRunning', 'Translating in the background…'],
  DONE: ['fnAdmin.autoDone', 'Automatic translation finished'],
  FAILED: ['fnAdmin.autoFailed', 'Automatic translation failed'],
  SKIPPED: ['fnAdmin.autoSkipped', 'Automatic translation skipped'],
} as const;

export function FunctionAutoTranslate({ record, language, blocked, busy }: Props) {
  const { t, available, locale } = useI18n();
  const capabilities = useTranslationCapabilities();
  const translate = useAutoTranslateFunction(record.id);
  const requeue = useRequeueTranslation(record.id);
  const [error, setError] = useState<unknown>(null);
  const lastAttempt = useRef<Attempt | null>(null);
  const { mutateAsync } = translate;

  const canTranslate = capabilities.data?.available === true;
  const automatic = capabilities.data?.automatic === true;
  const isDraft = isEditableStatus(record.status);
  const unsupported = new Set((record.machineTranslation?.unsupportedLanguages ?? []).map((code) => code.toLowerCase()));
  const missing = available.map((item) => item.code).filter((code) => !hasFunctionTranslation(record, code));
  const translatable = missing.filter((code) => !unsupported.has(code.toLowerCase()));
  const manualOnly = missing.filter((code) => unsupported.has(code.toLowerCase()));
  const machine =
    record.nameTranslations?.[language]?.source === 'machine' || record.descriptionTranslations?.[language]?.source === 'machine';
  const job = record.machineTranslation ?? null;

  const generate = useCallback(
    async (languages: string[], overwriteMachine = false) => {
      if (busy || blocked || !canTranslate || !isDraft || languages.length === 0) return;
      lastAttempt.current = { languages, overwriteMachine };
      setError(null);
      try {
        await mutateAsync({ languages, overwriteMachine });
        toast.success(t('fnAdmin.translationSuccess'));
      } catch (failure) {
        setError(failure);
      }
    },
    [busy, blocked, canTranslate, isDraft, mutateAsync, t],
  );

  async function queueAgain() {
    setError(null);
    try {
      await requeue.mutateAsync();
      toast.success(t('fnAdmin.autoRequeued', 'Queued for automatic translation'));
    } catch (failure) {
      setError(failure);
    }
  }

  if (capabilities.isPending) return <SkeletonText lines={2} />;
  if (capabilities.error) return <FunctionErrorState error={capabilities.error} onRetry={() => void capabilities.refetch()} />;
  if (!canTranslate) return <InlineAlert tone="info">{t('fnAdmin.autoUnavailable')}</InlineAlert>;
  if (!isDraft) return null;

  return (
    <div className="space-y-3 rounded-card-sm bg-surface p-4">
      <div className="flex items-start gap-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-pill bg-background text-foreground" aria-hidden="true">
          <SparklesIcon className="h-4 w-4" />
        </span>
        <div className="min-w-0 text-sm">
          <p className="font-medium text-foreground">{t('fnAdmin.autoTitle', 'Automatic translation')}</p>
          {missing.length > 0 ? (
            <p className="mt-0.5 text-secondary">
              {t('fnAdmin.translationGaps')} <span className="micro">{missing.join(', ')}</span>
            </p>
          ) : (
            <p className="mt-0.5 text-secondary">{t('fnAdmin.allTranslated', 'Every active language is covered.')}</p>
          )}
          {manualOnly.length > 0 && (
            <p className="mt-0.5 text-xs text-secondary">
              {t('fnAdmin.autoUnsupported', { languages: manualOnly.join(', ') })}
            </p>
          )}
          {automatic && (
            <p className="mt-0.5 text-xs text-secondary">
              {t('fnAdmin.autoBackground', 'Missing languages are translated automatically in the background; existing translations are never replaced.')}
            </p>
          )}
        </div>
      </div>
      {job?.status && <JobStatus job={job} locale={locale} t={t} />}
      <div className="flex flex-wrap gap-2">
        {translatable.length > 0 && (
          <Button variant="dark" size="sm" icon={<SparklesIcon />} loading={translate.isPending} disabled={blocked || busy} onClick={() => void generate(translatable)}>
            {t('fnAdmin.translate')}
          </Button>
        )}
        {machine && language !== record.sourceLanguage && (
          <Button variant="outline" size="sm" icon={<RefreshCwIcon />} disabled={blocked || busy} onClick={() => void generate([language], true)}>
            {t('fnAdmin.regenerate')}
          </Button>
        )}
        {automatic && (job?.status === 'FAILED' || job?.status === 'SKIPPED') && (
          <Button variant="outline" size="sm" icon={<RotateCcwIcon />} loading={requeue.isPending} disabled={busy} onClick={() => void queueAgain()}>
            {t('fnAdmin.autoRequeue', 'Queue again')}
          </Button>
        )}
      </div>
      {Boolean(error) && (
        <InlineAlert
          tone="danger"
          action={
            lastAttempt.current ? (
              <Button
                variant="outline"
                size="sm"
                disabled={blocked || busy}
                onClick={() => {
                  const attempt = lastAttempt.current;
                  if (attempt) void generate(attempt.languages, attempt.overwriteMachine);
                }}
              >
                {t('fnAdmin.retryLater')}
              </Button>
            ) : undefined
          }
        >
          {errorMessage(error, t)}
        </InlineAlert>
      )}
    </div>
  );
}

function JobStatus({ job, locale, t }: { job: MachineTranslationSummary; locale: string; t: Translate }) {
  const status = job.status ?? 'PENDING';
  const [key, fallback] = STATUS_LABELS[status];
  const tone = status === 'FAILED' ? 'danger' : status === 'DONE' ? 'success' : status === 'SKIPPED' ? 'warning' : 'info';
  const details: string[] = [];
  if (status === 'PENDING' && job.nextAttemptAt) details.push(`${t('fnAdmin.autoNextAttempt', 'Next attempt')}: ${formatDateTime(job.nextAttemptAt, locale)}`);
  if (status === 'DONE' && job.finishedAt) details.push(formatDateTime(job.finishedAt, locale));
  if (job.attempts > 1 || status === 'FAILED') details.push(`${t('fnAdmin.autoAttempts', 'Attempts')}: ${job.attempts}`);
  return (
    <InlineAlert tone={tone}>
      <span className="block">
        <span className="font-medium">{t(key, fallback)}</span>
        {details.length > 0 && <span className="text-secondary"> · {details.join(' · ')}</span>}
      </span>
      {job.lastError && <span className="mt-1 block text-xs" lang="en">{job.lastError}</span>}
    </InlineAlert>
  );
}
