const reported = new Set<string>();

declare global {
  interface Window {
    /** Keys looked up during this page's life that no catalog layer defines (development builds only; read by tests). */
    __i18nMissingKeys?: string[];
  }
}

/**
 * Records a key that neither the server dictionary nor the shipped catalog defines. The interface shows readable text
 * instead (see humanizeKey); development builds warn once per key and list them for the UI tests.
 */
export function reportMissingKey(key: string): void {
  if (reported.has(key)) return;
  reported.add(key);
  if (!import.meta.env.DEV || typeof window === 'undefined') return;
  (window.__i18nMissingKeys ??= []).push(key);
  console.warn(`[i18n] missing translation key: ${key}`);
}
