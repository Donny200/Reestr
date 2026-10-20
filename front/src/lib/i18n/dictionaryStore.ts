import { readStored, writeStored } from '../storage';
import type { Dictionary } from './translate';

const PREFIX = 'reestr-i18n-dictionary:';
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;

interface StoredDictionary {
  savedAt: number;
  values: Dictionary;
}

/**
 * Last dictionary received per locale, kept in localStorage so a returning visitor sees the interface in their
 * language at once and while offline; it is always revalidated against the server (ETag) after rendering.
 */
export function readDictionary(locale: string): Dictionary | null {
  const raw = readStored(PREFIX + locale.toLowerCase());
  if (!raw) return null;
  try {
    const stored = JSON.parse(raw) as StoredDictionary;
    if (!stored || typeof stored.values !== 'object' || Date.now() - stored.savedAt > MAX_AGE_MS) return null;
    return stored.values;
  } catch {
    return null;
  }
}

export function writeDictionary(locale: string, values: Dictionary): void {
  writeStored(PREFIX + locale.toLowerCase(), JSON.stringify({ savedAt: Date.now(), values } satisfies StoredDictionary));
}
