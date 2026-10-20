import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MonitorSmartphoneIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { SkeletonText } from '../../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { useI18n } from '../../contexts/i18n';
import { authKeys } from '../queryKeys';
import { getUserSessions, revokeUserSession, revokeUserSessions } from '../../services/legacyUserService';
import type { LegacyUser, SessionInfo } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { fullName } from '../../utils/format';
import { SessionList } from './SessionsCard';

/** SUPER_ADMIN view of another user's sessions; the list never contains token material. */
export function UserSessionsDialog({ user, onClose }: { user: LegacyUser | null; onClose: () => void }) {
  const { t, locale } = useI18n();
  const client = useQueryClient();
  const id = user?.id ?? 0;
  const sessions = useQuery({ queryKey: authKeys.userSessions(id), queryFn: () => getUserSessions(id), enabled: user !== null });
  const invalidate = () => client.invalidateQueries({ queryKey: authKeys.userSessions(id) });
  const revoke = useMutation({
    mutationFn: (session: SessionInfo) => revokeUserSession(id, session.sessionId),
    onSuccess: async () => { await invalidate(); toast.success(t('sessions.revoked', 'Session ended')); },
    onError: (error) => toast.error(errorMessage(error, t)),
  });
  const revokeAll = useMutation({
    mutationFn: () => revokeUserSessions(id),
    onSuccess: async () => { await invalidate(); toast.success(t('sessions.allRevoked', 'All sessions ended')); },
    onError: (error) => toast.error(errorMessage(error, t)),
  });
  const list = sessions.data ?? [];

  return (
    <Modal
      open={user !== null}
      onClose={onClose}
      size="lg"
      icon={<MonitorSmartphoneIcon aria-hidden="true" />}
      title={t('sessions.title', 'Active sessions')}
      description={user ? `${fullName(user)} · ${user.email}` : undefined}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>{t('action.close')}</Button>
          {list.length > 0 && <Button variant="danger" loading={revokeAll.isPending} onClick={() => revokeAll.mutate()}>{t('sessions.revokeAll', 'End all sessions')}</Button>}
        </>
      }
    >
      {sessions.isPending ? (
        <SkeletonText lines={3} />
      ) : sessions.error ? (
        <ErrorState error={sessions.error} onRetry={() => void sessions.refetch()} />
      ) : list.length === 0 ? (
        <EmptyState title={t('sessions.empty', 'No active sessions')} />
      ) : (
        <SessionList sessions={list} locale={locale} revoking={revoke.isPending ? revoke.variables.sessionId : null} onRevoke={(session) => revoke.mutate(session)} revokeLabel={t('sessions.revoke', 'End session')} />
      )}
    </Modal>
  );
}
