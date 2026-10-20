import { useCallback, useEffect } from 'react';
import { useAuth } from '../../contexts/auth';
import { recordEngagement } from '../../services/analyticsService';
import type { EngagementEvent } from '../../types/analytics';

const STORAGE_KEY = 'reestr-engagement-recent';
const VIEW_WINDOW_MS = 30 * 60_000;
const ACTION_WINDOW_MS = 10_000;

function optedOut(): boolean {
  if (typeof navigator === 'undefined') return true;
  const privacy = navigator as Navigator & { globalPrivacyControl?: boolean };
  return privacy.globalPrivacyControl === true || navigator.doNotTrack === '1';
}

function keyOf(event: EngagementEvent): string {
  return [event.type, event.serviceId ?? '', event.organizationId ?? ''].join(':');
}

function firstInWindow(event: EngagementEvent, now: number): boolean {
  const windowMs = event.type === 'CATALOG_VIEW' || event.type === 'SERVICE_VIEW' ? VIEW_WINDOW_MS : ACTION_WINDOW_MS;
  try {
    const recent = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '{}') as Record<string, number>;
    const last = recent[keyOf(event)];
    if (typeof last === 'number' && now - last < windowMs) return false;
    const kept = Object.fromEntries(Object.entries(recent).filter(([, at]) => typeof at === 'number' && now - at < VIEW_WINDOW_MS));
    kept[keyOf(event)] = now;
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(kept));
    return true;
  } catch {
    return true;
  }
}

export function useEngagementTracker() {
  const { user, initializing } = useAuth();
  const visitor = !initializing && !user;
  return useCallback(
    (event: EngagementEvent) => {
      if (!visitor || optedOut() || !firstInWindow(event, Date.now())) return;
      void recordEngagement(event).catch(() => undefined);
    },
    [visitor],
  );
}

export function usePageView(type: 'CATALOG_VIEW' | 'SERVICE_VIEW' | null, serviceId?: number, organizationId?: number) {
  const track = useEngagementTracker();
  useEffect(() => {
    if (type) track({ type, serviceId, organizationId });
  }, [type, serviceId, organizationId, track]);
}

export function usePrintTracking(serviceId: number | null) {
  const track = useEngagementTracker();
  useEffect(() => {
    if (serviceId === null) return undefined;
    const handler = () => track({ type: 'PRINT', serviceId });
    window.addEventListener('beforeprint', handler);
    return () => window.removeEventListener('beforeprint', handler);
  }, [serviceId, track]);
}
