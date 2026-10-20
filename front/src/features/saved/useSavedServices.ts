import { useCallback, useSyncExternalStore } from 'react';
import {
  getSavedServices,
  getServerSnapshot,
  SAVED_SERVICES_LIMIT,
  subscribeSavedServices,
  writeSavedServices,
  type SavedService,
} from './savedServicesStore';

export interface SaveTarget {
  id: number;
  name: string;
}

export function useSavedServices() {
  const items = useSyncExternalStore(subscribeSavedServices, getSavedServices, getServerSnapshot);

  const isSaved = useCallback((id: number) => items.some((item) => item.id === id), [items]);

  const save = useCallback((target: SaveTarget) => {
    const current = getSavedServices();
    if (current.some((item) => item.id === target.id) || current.length >= SAVED_SERVICES_LIMIT) return false;
    const entry: SavedService = { id: target.id, name: target.name, savedAt: new Date().toISOString() };
    return writeSavedServices([entry, ...current]);
  }, []);

  const remove = useCallback((id: number) => writeSavedServices(getSavedServices().filter((item) => item.id !== id)), []);

  const clear = useCallback(() => writeSavedServices([]), []);

  return { items, isSaved, save, remove, clear, full: items.length >= SAVED_SERVICES_LIMIT };
}
