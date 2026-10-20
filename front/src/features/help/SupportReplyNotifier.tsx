import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { useLiveSupport } from './liveSupport';
import { lastNotifiedReply, rememberNotifiedReply, useSupportActive } from './supportActivity';
import { useSupportBadge } from './useSupportBadge';

/**
 * Keeps the visitor's live support stream open while they browse the public site (only where a thread can exist)
 * and announces each new support reply once.
 */
export function SupportReplyNotifier() {
  const { t } = useI18n();
  const { user } = useAuth();
  const active = useSupportActive();
  const navigate = useNavigate();
  const location = useLocation();
  useLiveSupport('visitor', Boolean(user) || active, `${user?.id ?? 'guest'}-${active}`);
  const unread = useSupportBadge();

  useEffect(() => {
    const latest = unread?.latestReplyId ?? 0;
    if (!unread || unread.unread === 0 || latest <= lastNotifiedReply()) return;
    rememberNotifiedReply(latest);
    if (location.pathname === '/support') return;
    toast.info(t('support.replyArrived', 'Support replied to your message'), {
      action: { label: t('support.openSupport', 'Open support'), onClick: () => navigate('/support') },
    });
  }, [unread, location.pathname, navigate, t]);

  return null;
}
