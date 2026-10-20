export interface SavedService {
  id: number;
  name: string;
  savedAt: string;
}

export const SAVED_SERVICES_KEY = 'reestr-saved-services';
export const SAVED_SERVICES_LIMIT = 100;

const EMPTY: SavedService[] = [];
const listeners = new Set<() => void>();
let cache: SavedService[] | null = null;

function isSavedService(value: unknown): value is SavedService {
  if (!value || typeof value !== 'object') return false;
  const item = value as Record<string, unknown>;
  return Number.isInteger(item.id) && (item.id as number) > 0 && typeof item.name === 'string' && typeof item.savedAt === 'string';
}

function read(): SavedService[] {
  try {
    const raw = window.localStorage.getItem(SAVED_SERVICES_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter(isSavedService).slice(0, SAVED_SERVICES_LIMIT) : EMPTY;
  } catch {
    return EMPTY;
  }
}

function notify() {
  listeners.forEach((listener) => listener());
}

export function getSavedServices(): SavedService[] {
  if (cache === null) cache = read();
  return cache;
}

export function getServerSnapshot(): SavedService[] {
  return EMPTY;
}

export function writeSavedServices(items: SavedService[]): boolean {
  cache = items.slice(0, SAVED_SERVICES_LIMIT);
  notify();
  try {
    if (cache.length === 0) window.localStorage.removeItem(SAVED_SERVICES_KEY);
    else window.localStorage.setItem(SAVED_SERVICES_KEY, JSON.stringify(cache));
    return true;
  } catch {
    return false;
  }
}

export function subscribeSavedServices(listener: () => void): () => void {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key !== null && event.key !== SAVED_SERVICES_KEY) return;
    cache = null;
    listener();
  };
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', onStorage);
  };
}
