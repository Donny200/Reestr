/**
 * A non-secret hint that this browser has used support, so the unread badge only polls where a thread can exist.
 * The guest secret itself lives in an HttpOnly cookie that JavaScript cannot read.
 */
import { useSyncExternalStore } from 'react';

const ACTIVE_KEY = 'reestr-support-active';
const NOTIFIED_KEY = 'reestr-support-notified-reply';
const CHANGE_EVENT = 'reestr-support-active-change';

export function supportActive(): boolean {
  try {
    return window.localStorage.getItem(ACTIVE_KEY) === '1';
  } catch {
    return false;
  }
}

let activeThisVisit = false;

export function markSupportActive(): void {
  activeThisVisit = true;
  try {
    window.localStorage.setItem(ACTIVE_KEY, '1');
  } catch {
    // Storage blocked: the badge then works only for this visit, while signed in or on the support page.
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

export function forgetSupportActive(): void {
  activeThisVisit = false;
  try {
    window.localStorage.removeItem(ACTIVE_KEY);
    window.localStorage.removeItem(NOTIFIED_KEY);
  } catch {
    // Nothing stored.
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

/** Reactive {@link supportActive}: true once this browser has a support thread (updates after the first message). */
export function useSupportActive(): boolean {
  return useSyncExternalStore(
    (listener) => {
      window.addEventListener(CHANGE_EVENT, listener);
      window.addEventListener('storage', listener);
      return () => {
        window.removeEventListener(CHANGE_EVENT, listener);
        window.removeEventListener('storage', listener);
      };
    },
    () => activeThisVisit || supportActive(),
    () => false,
  );
}

/** Highest reply id the visitor was already told about (so a reply is announced once). */
export function lastNotifiedReply(): number {
  try {
    return Number(window.localStorage.getItem(NOTIFIED_KEY) ?? '0') || 0;
  } catch {
    return 0;
  }
}

export function rememberNotifiedReply(id: number): void {
  try {
    window.localStorage.setItem(NOTIFIED_KEY, String(id));
  } catch {
    // Storage blocked: the reply may be announced again after a reload.
  }
}

/** Idempotency key for one message; retries reuse it so the server stores the message once. */
export function newClientMessageId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}
