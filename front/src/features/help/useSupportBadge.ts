import { useAuth } from '../../contexts/auth';
import { useSupportUnread } from './queries';
import { useSupportActive } from './supportActivity';

/** Unread staff replies for the public navigation; loads only where a support thread can exist. */
export function useSupportBadge() {
  const { user } = useAuth();
  const active = useSupportActive();
  return useSupportUnread(Boolean(user) || active).data;
}
