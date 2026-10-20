import { LanguagesIcon, RefreshCwIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { SkeletonText } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import type { InterfaceLanguageCoverage } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, formatPercent } from '../../utils/format';
import { languageDisplayName } from '../../lib/i18n/locale';
import { useQueueMachineTranslation, useTranslationOverview } from './queries';

/**
 * How complete each active interface language is (what a visitor of that language sees, variants inheriting from
 * their parent language), where the values come from, and the state of filling the gaps by machine translation.
 */
export function TranslationCoverageCard() {
  const { t, locale } = useI18n();
  const { hasPermission } = useAuth();
  const canView = hasPermission('TRANSLATIONS_VIEW_COVERAGE');
  const canQueue = hasPermission('TRANSLATIONS_EDIT');
  const overview = useTranslationOverview(canView);
  const queue = useQueueMachineTranslation();

  if (!canView) return null;

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
                            {job.lastError && <span className="wrap-anywhere" lang="en">{job.lastError}</span>}
                          </p>
                        )}
                        {canQueue && item.supported && (item.missingKeys > 0 || item.staleMachineKeys > 0 || job?.status === 'FAILED') && (
                          <Button
                            className="mt-2"
                            size="sm"
                            variant="outline"
                            icon={<RefreshCwIcon />}
                            disabled={!overview.data.providerConfigured}
                            loading={queue.isPending && queue.variables === item.languageCode}
                            onClick={() => fill(item)}
                          >
                            {job?.status === 'FAILED' ? t('lang.machineRetry') : t('lang.machineFill')}
                          </Button>
                        )}
                      </>
                    )}
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
