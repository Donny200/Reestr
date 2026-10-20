import { useCallback, useMemo, useSyncExternalStore } from 'react';
import type { CatalogFunction } from '../../types/api';
import {
  checklistItems,
  clearChecklist,
  getChecklist,
  getChecklistServerSnapshot,
  subscribeChecklist,
  writeChecklist,
  type ChecklistItem,
  type StoredChecklist,
  type StoredChecklistItem,
} from './checklistStore';

export interface StaleItem extends StoredChecklistItem {
  key: string;
}

/**
 * Ticked items survive translation switches and unrelated edits because they are keyed by the source text; an item
 * whose text was changed or removed by the organization is reported separately instead of being silently dropped.
 * Every component on the page shares the same store, so the progress header updates with each tick.
 */
export function useChecklist(record: CatalogFunction, locale: string) {
  const serviceId = record.id;
  const subscribe = useCallback((listener: () => void) => subscribeChecklist(serviceId, listener), [serviceId]);
  const stored = useSyncExternalStore(subscribe, () => getChecklist(serviceId), getChecklistServerSnapshot);

  const items: ChecklistItem[] = useMemo(() => checklistItems(record, locale), [record, locale]);
  const currentKeys = useMemo(() => new Set(items.map((item) => item.key)), [items]);
  const stale: StaleItem[] = useMemo(
    () => Object.entries(stored.items).filter(([key]) => !currentKeys.has(key)).map(([key, item]) => ({ key, ...item })),
    [stored.items, currentKeys],
  );
  const doneCount = items.filter((item) => stored.items[item.key]).length;

  const toggle = useCallback(
    (item: ChecklistItem) => {
      const current = getChecklist(serviceId);
      const next: StoredChecklist = { ...current, items: { ...current.items } };
      if (next.items[item.key]) delete next.items[item.key];
      else next.items[item.key] = { section: item.section, text: item.text, doneAt: new Date().toISOString() };
      writeChecklist(next);
    },
    [serviceId],
  );

  const removeStale = useCallback(() => {
    const current = getChecklist(serviceId);
    const next: StoredChecklist = { ...current, items: { ...current.items } };
    Object.keys(next.items).forEach((key) => {
      if (!currentKeys.has(key)) delete next.items[key];
    });
    writeChecklist(next);
  }, [serviceId, currentKeys]);

  const clear = useCallback(() => clearChecklist(serviceId), [serviceId]);
  const isDone = useCallback((item: ChecklistItem) => Boolean(stored.items[item.key]), [stored.items]);

  return { items, stale, doneCount, isDone, toggle, removeStale, clear, persisted: stored.persisted !== false };
}
