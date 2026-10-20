import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getInterfaceTranslations, getLanguages } from '../services/referenceService';
import { readStored, writeStored } from '../lib/storage';
import { I18nContext, type I18nValue, type InterfaceLanguage } from './i18n';
const DEFAULT_LOCALE = 'uz';
const STORAGE_KEY = 'reestr-task-locale';
const RTL_LANGUAGES = new Set(['ar', 'ckb', 'dv', 'fa', 'he', 'ku', 'ps', 'sd', 'ug', 'ur', 'yi']);
const RTL_SCRIPTS = new Set(['Adlm', 'Arab', 'Hebr', 'Mand', 'Nkoo', 'Rohg', 'Samr', 'Syrc', 'Thaa']);
const DICTIONARY_TIMEOUT_MS = 5_000;

function directionForLocale(locale: string): 'ltr' | 'rtl' {
  const parts = locale.replace(/_/g, '-').split('-');
  const script = parts.find((part) => /^[A-Za-z]{4}$/.test(part));
  if (script) {
    const canonicalScript = `${script[0].toUpperCase()}${script.slice(1).toLowerCase()}`;
    return RTL_SCRIPTS.has(canonicalScript) ? 'rtl' : 'ltr';
  }
  return RTL_LANGUAGES.has(parts[0].toLowerCase()) ? 'rtl' : 'ltr';
}

function initialLocale(): string {
  if (typeof window === 'undefined') return DEFAULT_LOCALE;
  return readStored(STORAGE_KEY) || DEFAULT_LOCALE;
}

export function I18nProvider({ children }: {children: ReactNode;}) {
  const [locale, setLocaleState] = useState<string>(initialLocale);
  const [contentLocale, setContentLocale] = useState<string>(initialLocale);
  const [available, setAvailable] = useState<InterfaceLanguage[]>([]);
  const [translations, setTranslations] = useState<Record<string, string>>({});
  const [ready, setReady] = useState(false);
  const dictionaryCache = useRef(new Map<string, Record<string, string>>());
  const lastSuccessfulLocale = useRef<string | null>(null);

  const setLocale = useCallback((nextLocale: string) => {
    setLocaleState(nextLocale);
    if (typeof window !== 'undefined') writeStored(STORAGE_KEY, nextLocale);
  }, []);

  const reloadLanguages = useCallback(async () => {
    try {
      const languages = await getLanguages();
      const nextAvailable = languages.map((language) => ({
        code: language.code,
        label: language.nativeName || language.name
      }));

      if (nextAvailable.length === 0) return;
      setAvailable(nextAvailable);
      setLocaleState((current) => {
        const currentLanguage = nextAvailable.find(
          (language) => language.code.toLowerCase() === current.toLowerCase()
        );
        if (currentLanguage) {
          if (typeof window !== 'undefined') writeStored(STORAGE_KEY, currentLanguage.code);
          return currentLanguage.code;
        }
        const fallback = nextAvailable.find((language) => language.code === DEFAULT_LOCALE) ?? nextAvailable[0];
        if (typeof window !== 'undefined') writeStored(STORAGE_KEY, fallback.code);
        return fallback.code;
      });
    } catch {
      setAvailable([]);
    }
  }, []);

  useEffect(() => {
    void reloadLanguages();
  }, [reloadLanguages]);

  useEffect(() => {
    document.documentElement.lang = contentLocale;
    document.documentElement.dir = directionForLocale(contentLocale);
  }, [contentLocale]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), DICTIONARY_TIMEOUT_MS);
    const cached = dictionaryCache.current.get(locale);
    if (cached) {
      lastSuccessfulLocale.current = locale;
      setTranslations(cached);
      setContentLocale(locale);
      setReady(true);
      window.clearTimeout(timeout);
      return () => controller.abort();
    }

    getInterfaceTranslations(locale, controller.signal)
      .then((dictionary) => {
        dictionaryCache.current.set(locale, dictionary);
        if (!cancelled) {
          lastSuccessfulLocale.current = locale;
          setTranslations(dictionary);
          setContentLocale(locale);
        }
      })
      .catch(() => {
        if (cancelled) return;
        const fallbackLocale = lastSuccessfulLocale.current;
        if (fallbackLocale && fallbackLocale !== locale) {
          setLocaleState(fallbackLocale);
          writeStored(STORAGE_KEY, fallbackLocale);
        } else {
          setTranslations({});
        }
      })
      .finally(() => {
        window.clearTimeout(timeout);
        if (!cancelled) setReady(true);
      });

    return () => {
      cancelled = true;
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [locale]);

  const t = useCallback(
    (key: string, fallback?: string): string => {
      return translations[key] || fallback || key;
    },
    [translations]
  );

  const value = useMemo<I18nValue>(
    () => ({ locale, setLocale, t, available, reloadLanguages }),
    [locale, setLocale, t, available, reloadLanguages]
  );

  if (!ready) {
    return (
      <div className="flex min-h-dvh items-center justify-center" role="status" aria-label="Loading">
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-line border-t-brand motion-reduce:animate-none" aria-hidden="true" />
      </div>
    );
  }

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}
