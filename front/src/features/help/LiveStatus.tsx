import { RadioIcon, RefreshCwIcon } from 'lucide-react';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import { useLiveState, type LiveChannel } from './liveSupport';

/** Whether new messages arrive by themselves (live stream) or by periodic checks (fallback). */
export function LiveStatus({ channel, pollSeconds, className }: { channel: LiveChannel; pollSeconds: number; className?: string }) {
  const { t } = useI18n();
  const state = useLiveState(channel);
  const live = state === 'live';
  return (
    <p className={cn('inline-flex items-center gap-1.5 text-xs text-secondary', className)} data-live-state={state}>
      {live ? <RadioIcon className="h-3.5 w-3.5 text-status-published" aria-hidden="true" /> : <RefreshCwIcon className="h-3.5 w-3.5" aria-hidden="true" />}
      {live
        ? t('support.liveOn', 'Live: new messages appear automatically')
        : t('support.livePolling', 'Checking for new messages every {seconds} seconds').replace('{seconds}', String(pollSeconds))}
    </p>
  );
}
