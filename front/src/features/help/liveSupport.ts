import { useEffect, useSyncExternalStore } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { helpKeys } from '../queryKeys';
import { openEventStream, type StreamState } from './eventStream';

/**
 * Near-real-time support updates. One stream per tab and side (visitor thread, staff inbox), opened only while the
 * tab is visible. Events only trigger reloads of the existing queries; polling continues as a fallback (slowly while
 * the stream is live, at the normal pace otherwise).
 */
export type LiveChannel = 'visitor' | 'staff';

/** Fallback polling while no stream is connected, and the slow safety net while one is. */
export const LIVE_SAFETY_POLL_MS = 60_000;
export const THREAD_POLL_MS = 10_000;
export const INBOX_POLL_MS = 15_000;
export const BADGE_POLL_MS = 30_000;

const states: Record<LiveChannel, StreamState> = { visitor: 'paused', staff: 'paused' };
const listeners = new Set<() => void>();

function setState(channel: LiveChannel, state: StreamState) {
  if (states[channel] === state) return;
  states[channel] = state;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useLiveState(channel: LiveChannel): StreamState {
  return useSyncExternalStore(subscribe, () => states[channel], () => 'paused' as StreamState);
}

/** Polling interval for a query of this channel: slow while live, `fallback` otherwise. */
export function useLiveInterval(channel: LiveChannel, fallback: number): number {
  return useLiveState(channel) === 'live' ? LIVE_SAFETY_POLL_MS : fallback;
}

function useVisibility(): boolean {
  return useSyncExternalStore(
    (listener) => {
      document.addEventListener('visibilitychange', listener);
      return () => document.removeEventListener('visibilitychange', listener);
    },
    () => document.visibilityState === 'visible',
    () => true,
  );
}

function parse(data: string): Record<string, unknown> {
  try {
    const value = JSON.parse(data) as unknown;
    return value && typeof value === 'object' ? (value as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

/**
 * Keeps one live stream open while `enabled` and the tab is visible. `restartKey` reconnects (e.g. after the
 * visitor's first message created their support identity).
 */
export function useLiveSupport(channel: LiveChannel, enabled: boolean, restartKey: unknown = null) {
  const client = useQueryClient();
  const visible = useVisibility();

  useEffect(() => {
    if (!enabled || !visible) {
      setState(channel, 'paused');
      return undefined;
    }
    const reloadAll = () => {
      if (channel === 'visitor') {
        void client.invalidateQueries({ queryKey: helpKeys.thread() });
        void client.invalidateQueries({ queryKey: helpKeys.unread() });
      } else {
        void client.invalidateQueries({ queryKey: helpKeys.inboxes() });
        void client.invalidateQueries({ queryKey: helpKeys.inboxUnread() });
        void client.invalidateQueries({ queryKey: helpKeys.details() });
      }
    };
    // Becoming visible again (or enabling the stream) reloads at once; the stream's "ready" event reloads again.
    reloadAll();
    const close = openEventStream({
      path: channel === 'visitor' ? '/api/help/support/stream' : '/api/help/inbox/stream',
      refreshOnUnauthorized: channel === 'staff',
      onState: (state) => setState(channel, state),
      onReady: reloadAll,
      onEvent: (event) => {
        const data = parse(event.data);
        if (channel === 'visitor' && event.event === 'support') {
          void client.invalidateQueries({ queryKey: helpKeys.thread() });
          void client.invalidateQueries({ queryKey: helpKeys.unread() });
        } else if (channel === 'staff' && event.event === 'inbox') {
          void client.invalidateQueries({ queryKey: helpKeys.inboxes() });
          void client.invalidateQueries({ queryKey: helpKeys.inboxUnread() });
          if (typeof data.conversationId === 'number') void client.invalidateQueries({ queryKey: helpKeys.detail(data.conversationId) });
        }
      },
    });
    return () => {
      close();
      setState(channel, 'paused');
    };
  }, [channel, client, enabled, visible, restartKey]);
}
