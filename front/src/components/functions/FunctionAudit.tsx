import { RefreshCwIcon } from 'lucide-react';
import { useI18n } from '../../contexts/i18n';
import { useFunctionAudit } from '../../features/functions/queries';
import { FunctionErrorState } from '../../features/functions/FunctionErrorState';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { StatusPill, type StatusTone } from '../ui/Badge';
import { Button } from '../ui/Button';
import { SkeletonText } from '../ui/Skeleton';
import { EmptyState } from '../ui/States';
import { formatDateTime } from '../../utils/format';

const tones: Record<string, StatusTone> = {
  PUBLISH: 'published',
  REJECT: 'danger',
  DEACTIVATE: 'danger',
  DELETE: 'danger',
  SUBMIT_REVIEW: 'pending',
  SUBMIT_FOR_REVIEW: 'pending',
  REACTIVATE: 'pending',
  WITHDRAW: 'pending',
  REVISION_CREATE: 'draft',
  REVISION_DISCARD: 'danger',
};

function normalizeAction(action: string): string {
  return action.toUpperCase().replace(/-/g, '_');
}

function humanize(action: string): string {
  const words = action.toLowerCase().split('_').join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function FunctionAudit({ functionId }: { functionId: number }) {
  const { t, locale } = useI18n();
  const history = useFunctionAudit(functionId);

  return (
    <Card>
      <CardHeader
        title={t('fnAdmin.audit')}
        actions={
          <Button variant="ghost" size="sm" icon={<RefreshCwIcon />} disabled={history.isFetching} onClick={() => void history.refetch()}>
            {t('fnAdmin.refresh')}
          </Button>
        }
      />
      <CardBody>
        {history.isPending ? (
          <SkeletonText lines={5} />
        ) : history.error ? (
          <FunctionErrorState error={history.error} onRetry={() => void history.refetch()} />
        ) : !history.data?.length ? (
          <EmptyState title={t('state.emptyTitle')} />
        ) : (
          <ol className="relative space-y-6 border-s border-line ps-6">
            {history.data.map((entry) => {
              const action = normalizeAction(entry.action);
              return (
                <li key={entry.id} className="relative">
                  <span className="absolute -start-[1.95rem] top-1.5 h-3 w-3 rounded-pill bg-ink ring-4 ring-background" aria-hidden="true" />
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <StatusPill tone={tones[action] ?? 'draft'}>{t(`fnAdmin.action.${action}`, humanize(action))}</StatusPill>
                    <time className="text-xs tabular-nums text-secondary" dateTime={entry.performedAt}>{formatDateTime(entry.performedAt, locale)}</time>
                  </div>
                  <p className="mt-1.5 text-sm text-foreground wrap-anywhere">{t('fnAdmin.actor')}: {entry.performedBy}</p>
                  {entry.details && <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-secondary wrap-anywhere">{entry.details}</p>}
                </li>
              );
            })}
          </ol>
        )}
      </CardBody>
    </Card>
  );
}
