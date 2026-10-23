import { useEffect, useRef, useState } from 'react';
import { DownloadIcon, LanguagesIcon, RefreshCwIcon, UploadIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { SkeletonText } from '../../components/ui/Skeleton';
import { ErrorState, TechnicalDetails } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { exportInterfaceTranslations } from '../../services/referenceService';
import type { InterfaceLanguageCoverage, InterfaceTranslationJobStatus } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, formatPercent } from '../../utils/format';
import { languageDisplayName } from '../../lib/i18n/locale';
import { useImportTranslations, useQueueMachineTranslation, useTranslationOverview } from './queries';

const ACTIVE: ReadonlySet<InterfaceTranslationJobStatus | undefined> = new Set(['PENDING', 'RUNNING']);

/**
 * How complete each active interface language is (what a visitor of that language sees, variants inheriting from
 * their parent language), where the values come from, and the state of filling the gaps by machine translation.
 */
export function TranslationCoverageCard() {
  const { t, locale, refreshDictionary } = useI18n();
  const { hasPermission } = useAuth();
  const canView = hasPermission('TRANSLATIONS_VIEW_COVERAGE');
  const canQueue = hasPermission('TRANSLATIONS_EDIT');
  const overview = useTranslationOverview(canView);
  const queue = useQueueMachineTranslation();
  const importer = useImportTranslations();
  const [exporting, setExporting] = useState<string | null>(null);
  const importTarget = useRef<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const previousStatus = useRef(new Map<string, InterfaceTranslationJobStatus | undefined>());

  // A job that finished since the last poll wrote new values: reload the dictionary on screen if it uses them.
  useEffect(() => {
    const languages = overview.data?.languages ?? [];
    for (const item of languages) {
      const before = previousStatus.current.get(item.languageCode);
      const now = item.job?.status;
      if (ACTIVE.has(before) && !ACTIVE.has(now)) void refreshDictionary(item.languageCode).catch(() => undefined);
      previousStatus.current.set(item.languageCode, now);
    }
  }, [overview.data, refreshDictionary]);

  if (!canView) return null;

  const download = async (code?: string) => {
    setExporting(code ?? '*');
    try {
      await exportInterfaceTranslations(code);
    } catch (error) {
      toast.error(errorMessage(error, t));
    } finally {
      setExporting(null);
    }
  };

  const chooseBackup = (code: string) => {
    importTarget.current = code;
    fileInput.current?.click();
  };

  const restore = async (file: File | undefined) => {
    const code = importTarget.current;
    if (!file || !code) return;
    let backup: unknown;
    try {
      backup = JSON.parse(await file.text());
    } catch {
      toast.error(t('lang.importInvalid'));
      return;
    }
    importer.mutate({ code, backup }, {
      onSuccess: (result) => {
        toast.success(t('lang.importDone', {
          created: result.created, updated: result.updated, unchanged: result.unchanged, kept: result.keptHuman, skipped: result.skipped,
        }));
        void refreshDictionary(code).catch(() => undefined);
      },
      onError: (error) => toast.error(errorMessage(error, t)),
    });
  };

  const fill = (item: InterfaceLanguageCoverage) =>
    queue.mutate(item.languageCode, {
      onSuccess: () => toast.success(t('lang.machineQueued')),
      onError: (error) => toast.error(errorMessage(error, t)),
    });

  return (
    <Card>
      <CardHeader
        title={t('lang.coverageTitle')}
        description={t('lang.coverageHint')}
        icon={<LanguagesIcon className="h-4 w-4" aria-hidden="true" />}
        actions={
          <Button size="sm" variant="outline" icon={<DownloadIcon />} loading={exporting === '*'} onClick={() => void download()}>
            {t('lang.exportAll')}
          </Button>
        }
      />
      <input
        ref={fileInput}
        type="file"
        accept="application/json,.json"
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
        data-testid="translation-import-file"
        onChange={(event) => {
          void restore(event.target.files?.[0]);
          event.target.value = '';
        }}
      />
      <CardBody className="space-y-4">
        {overview.isPending ? (
          <SkeletonText lines={4} />
        ) : overview.error ? (
          <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />
        ) : (
          <>
            <p className="text-sm text-secondary" data-testid="machine-translation-state">
              {overview.data.automatic
                ? t('lang.machineOn')
                : overview.data.providerConfigured
                  ? t('lang.machinePaused')
                  : t('lang.machineOff')}
            </p>
            <p className="text-xs leading-5 text-secondary">{t('lang.backupHint')}</p>
            {!overview.data.activeLanguagesAvailable && <p className="text-sm text-status-danger">{t('lang.activeUnavailable')}</p>}
            <ul className="divide-y divide-line">
              {overview.data.languages.map((item) => {
                const complete = item.missingKeys === 0;
                const job = item.job;
                return (
                  <li key={item.languageCode} className="py-3" data-language={item.languageCode}>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="min-w-0">
                        <span className="font-medium text-foreground">{languageDisplayName(item.languageCode, locale)}</span>
                        <span className="ms-2 text-xs text-secondary">{item.languageCode}</span>
                      </span>
                      <Badge size="sm" tone={complete ? 'accent' : 'neutral'}>
                        {item.englishBase ? t('lang.coverageSource') : formatPercent(item.percentage / 100, locale)}
                      </Badge>
                    </div>
                    {!item.englishBase && (
                      <>
                        <div className="mt-2 h-1.5 overflow-hidden rounded-pill bg-surface-2" aria-hidden="true">
                          <div className="h-full rounded-pill bg-ink" style={{ width: `${Math.min(100, item.percentage)}%` }} />
                        </div>
                        <p className="mt-2 text-xs leading-5 text-secondary">
                          {t('lang.coverageCounts', {
                            translated: item.translatedKeys,
                            required: item.requiredKeys,
                            human: item.humanKeys + item.seedKeys,
                            machine: item.machineKeys,
                          })}
                          {item.missingKeys > 0 && <> · {t('lang.missingCount', { count: item.missingKeys })}</>}
                          {item.staleMachineKeys > 0 && <> · {t('lang.staleCount', { count: item.staleMachineKeys })}</>}
                          {item.outdatedHumanKeys > 0 && <> · {t('lang.outdatedCount', { count: item.outdatedHumanKeys })}</>}
                        </p>
                        {item.missingSample.length > 0 && (
                          <details className="mt-1 text-xs text-secondary">
                            <summary className="cursor-pointer">{t('lang.missingSample')}</summary>
                            <p className="mt-1 wrap-anywhere font-mono" lang="en">{item.missingSample.join(', ')}</p>
                          </details>
                        )}
                        <p className="mt-1 text-xs text-secondary">
                          {item.supported
                            ? t('lang.providerCode', { code: item.providerCode ?? '' })
                            : t('lang.providerUnsupported')}
                        </p>
                        {job && (
                          <p className="mt-1 flex flex-wrap items-center gap-2 text-xs text-secondary">
                            <Badge size="sm" tone={job.status === 'DONE' ? 'accent' : 'neutral'}>{t(`lang.job.${job.status}`)}</Badge>
                            {job.status === 'PENDING' && job.attempts > 0 && (
                              <span>{t('lang.nextAttempt', { date: formatDateTime(job.nextAttemptAt, locale) })}</span>
                            )}
                            {job.finishedAt && job.status !== 'PENDING' && <span>{formatDateTime(job.finishedAt, locale)}</span>}
                          </p>
                        )}
                        {job?.status === 'FAILED' && <p className="mt-1 text-xs text-status-danger">{t('lang.jobFailedHint')}</p>}
                        {job?.status === 'DONE' && job.lastError && <p className="mt-1 text-xs text-secondary">{t('lang.jobPartialHint')}</p>}
                        {job?.lastError && <TechnicalDetails text={job.lastError} />}
                        {canQueue && item.supported && (item.missingKeys > 0 || item.staleMachineKeys > 0 || job?.status === 'FAILED') && (
                          <>
                            <Button
                              className="mt-2"
                              size="sm"
                              variant="outline"
                              icon={<RefreshCwIcon />}
                              disabled={!overview.data.automatic || ACTIVE.has(job?.status)}
                              loading={queue.isPending && queue.variables === item.languageCode}
                              onClick={() => fill(item)}
                            >
                              {job?.status === 'FAILED' ? t('lang.machineRetry') : t('lang.machineFill')}
                            </Button>
                            {!overview.data.automatic && (
                              <p className="mt-1 text-xs text-secondary">
                                {overview.data.providerConfigured ? t('lang.machineFillPaused') : t('lang.machineFillUnavailable')}
                              </p>
                            )}
                          </>
                        )}
                      </>
                    )}
                    <div className="mt-2 flex flex-wrap gap-2">
                      <Button size="sm" variant="ghost" icon={<DownloadIcon />} loading={exporting === item.languageCode}
                        onClick={() => void download(item.languageCode)}>
                        {t('lang.export')}
                      </Button>
                      {canQueue && (
                        <Button size="sm" variant="ghost" icon={<UploadIcon />}
                          loading={importer.isPending && importer.variables?.code === item.languageCode}
                          onClick={() => chooseBackup(item.languageCode)}>
                          {t('lang.import')}
                        </Button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </CardBody>
    </Card>
  );
}
