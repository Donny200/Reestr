import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { LogOutIcon, MonitorSmartphoneIcon } from 'lucide-react';
import { toast } from 'sonner';
import { StatusPill } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { SkeletonText } from '../../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { authKeys } from '../queryKeys';
import { getSessions, revokeOtherSessions, revokeSession } from '../../services/authService';
import type { SessionInfo } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { formatDateTime } from '../../utils/format';

export function SessionList({ sessions, onRevoke, revoking, locale, revokeLabel }: {
  sessions: SessionInfo[];
  onRevoke: (session: SessionInfo) => void;
  revoking: string | null;
  locale: string;
  revokeLabel: string;
}) {
  const { t } = useI18n();
  return (
    <ul className="divide-y divide-line" aria-label={t('sessions.title', 'Active sessions')}>
      {sessions.map((session) => (
        <li key={session.sessionId} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-foreground">
              <span className="wrap-anywhere">{session.userAgent ?? t('sessions.unknownDevice', 'Unknown device')}</span>
              {session.current && <StatusPill tone="published">{t('sessions.current', 'This browser')}</StatusPill>}
            </p>
            <p className="mt-0.5 text-xs tabular-nums text-secondary">
              {t('sessions.started', 'Started')} {formatDateTime(session.createdAt, locale)}
              {session.lastUsedAt && <> · {t('sessions.lastUsed', 'Last used')} {formatDateTime(session.lastUsedAt, locale)}</>}
              {' · '}{t('sessions.expires', 'Expires')} {formatDateTime(session.expiresAt, locale)}
            </p>
          </div>
          <Button variant="outline" size="sm" icon={<LogOutIcon />} loading={revoking === session.sessionId} disabled={revoking !== null} onClick={() => onRevoke(session)}>
            {revokeLabel}
            <span className="sr-only">: {session.userAgent ?? session.sessionId}</span>
          </Button>
        </li>
      ))}
    </ul>
  );
}

/** The user's own sessions. Revoking the current one signs this browser out. */
export function SessionsCard() {
  const { t, locale } = useI18n();
  const { signOut } = useAuth();
  const client = useQueryClient();
  const sessions = useQuery({ queryKey: authKeys.sessions(), queryFn: getSessions });
  const revoke = useMutation({
    mutationFn: (session: SessionInfo) => revokeSession(session.sessionId).then(() => session),
    onSuccess: async (session) => {
      if (session.current) {
        await signOut();
        return;
      }
      await client.invalidateQueries({ queryKey: authKeys.sessions() });
      toast.success(t('sessions.revoked', 'Session ended'));
    },
    onError: (error) => toast.error(errorMessage(error, t)),
  });
  const revokeOthers = useMutation({
    mutationFn: revokeOtherSessions,
    onSuccess: async (result) => {
      await client.invalidateQueries({ queryKey: authKeys.sessions() });
      toast.success(`${t('sessions.othersRevoked', 'Other sessions ended')}: ${result.revoked}`);
    },
    onError: (error) => toast.error(errorMessage(error, t)),
  });
  const list = sessions.data ?? [];

  return (
    <Card id="sessions">
      <CardHeader
        icon={<MonitorSmartphoneIcon />}
        title={t('sessions.title', 'Active sessions')}
        description={t('sessions.subtitle', 'Browsers that are currently signed in to your account. Ending a session signs that browser out.')}
        actions={list.length > 1 ? (
          <Button variant="outline" size="sm" loading={revokeOthers.isPending} onClick={() => revokeOthers.mutate()}>
            {t('sessions.revokeOthers', 'End all other sessions')}
          </Button>
        ) : undefined}
      />
      <CardBody>
        {sessions.isPending ? (
          <SkeletonText lines={3} />
        ) : sessions.error ? (
          <ErrorState error={sessions.error} onRetry={() => void sessions.refetch()} />
        ) : list.length === 0 ? (
          <EmptyState title={t('sessions.empty', 'No active sessions')} />
        ) : (
          <SessionList sessions={list} locale={locale} revoking={revoke.isPending ? revoke.variables.sessionId : null} onRevoke={(session) => revoke.mutate(session)} revokeLabel={t('sessions.revoke', 'End session')} />
        )}
      </CardBody>
    </Card>
  );
}
