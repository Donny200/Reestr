import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getInterfaceTranslations, getLanguages } from '../services/referenceService';
import { readStored, writeStored } from '../lib/storage';
import { ENGLISH_CATALOG, loadCuratedCatalog } from '../lib/i18n/catalog';
import { forgetDictionary, readDictionary, writeDictionary } from '../lib/i18n/dictionaryStore';
import { canonicalLocale, directionForLocale, sameLocale } from '../lib/i18n/locale';
import { reportMissingKey } from '../lib/i18n/missing';
import { createTranslate, type Dictionary } from '../lib/i18n/translate';
import { I18nContext, type I18nValue, type InterfaceLanguage } from './i18n';
const DEFAULT_LOCALE = 'uz';
const STORAGE_KEY = 'reestr-task-locale';
const DICTIONARY_TIMEOUT_MS = 5_000;
/** Shown only while the very first dictionary loads, before any translation is available (i18n-exempt). */
const BOOT_LABELS: Record<string, string> = { en: 'Loading…', ru: 'Загрузка…', uz: 'Yuklanmoqda…', fr: 'Chargement…' };

interface Applied {
  locale: string;
  /** What the server served (or the stored copy of it); the source of truth when it has a value. */
  dictionary: Dictionary;
  /** The shipped catalog of the locale, used where the server dictionary has no value of its own. */
  curated: Dictionary;
}

const NO_VALUES: Dictionary = {};

function initialLocale(): string {
  if (typeof window === 'undefined') return DEFAULT_LOCALE;
  return readStored(STORAGE_KEY) || DEFAULT_LOCALE;
}

function initialApplied(locale: string): Applied | null {
  const stored = typeof window === 'undefined' ? null : readDictionary(locale);
  return stored ? { locale, dictionary: stored, curated: NO_VALUES } : null;
}

/** `fr-FR` inherits from `fr` and from itself; the server overlays each level of the tag. */
function inheritsFrom(locale: string, ancestor: string): boolean {
  const code = canonicalLocale(locale).toLowerCase();
  const parent = canonicalLocale(ancestor).toLowerCase();
  return code === parent || code.startsWith(`${parent}-`);
}

function sameDictionary(left: Dictionary, right: Dictionary): boolean {
  const leftKeys = Object.keys(left);
  if (leftKeys.length !== Object.keys(right).length) return false;
  return leftKeys.every((key) => left[key] === right[key]);
}

/**
 * Interface language state. The dictionary of the requested locale is fetched once per session (and revalidated with
 * the server's ETag when a stored copy is shown first); labels, content language, formatting, `lang` and `dir` switch
 * together only when it has arrived, so a slow or failed request never mixes two languages on screen.
 */
export function I18nProvider({ children }: {children: ReactNode;}) {
  const [requestedLocale, setRequestedLocale] = useState<string>(initialLocale);
  const [applied, setApplied] = useState<Applied | null>(() => initialApplied(requestedLocale));
  const [available, setAvailable] = useState<InterfaceLanguage[]>([]);
  const dictionaryCache = useRef(new Map<string, Dictionary>());
  const curatedCache = useRef(new Map<string, Dictionary>());
  const appliedRef = useRef<Applied | null>(applied);
  appliedRef.current = applied;

  const setLocale = useCallback((nextLocale: string) => {
    setRequestedLocale((current) => (sameLocale(current, nextLocale) ? current : nextLocale));
    if (typeof window !== 'undefined') writeStored(STORAGE_KEY, nextLocale);
  }, []);

  const apply = useCallback((locale: string, dictionary: Dictionary, curated: Dictionary) => {
    setApplied((current) =>
      current && current.locale === locale && current.curated === curated && sameDictionary(current.dictionary, dictionary)
        ? current
        : { locale, dictionary, curated },
    );
  }, []);

  const curatedFor = useCallback(async (locale: string) => {
    const known = curatedCache.current.get(locale);
    if (known) return known;
    const curated = await loadCuratedCatalog(locale);
    curatedCache.current.set(locale, curated);
    return curated;
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
      setRequestedLocale((current) => {
        const currentLanguage = nextAvailable.find((language) => sameLocale(language.code, current));
        const next = currentLanguage ?? nextAvailable.find((language) => language.code === DEFAULT_LOCALE) ?? nextAvailable[0];
        if (typeof window !== 'undefined') writeStored(STORAGE_KEY, next.code);
        return next.code;
      });
    } catch {
      setAvailable([]);
    }
  }, []);

  useEffect(() => {
    void reloadLanguages();
  }, [reloadLanguages]);

  useEffect(() => {
    const cached = dictionaryCache.current.get(requestedLocale);
    const cachedCurated = curatedCache.current.get(requestedLocale);
    if (cached && cachedCurated) {
      apply(requestedLocale, cached, cachedCurated);
      return undefined;
    }
    let cancelled = false;
    const curated = curatedFor(requestedLocale);
    // A stored copy from an earlier visit is shown at once (with the shipped catalog under it for keys it predates);
    // the request below still refreshes it.
    const stored = readDictionary(requestedLocale);
    if (stored) {
      void curated.then((values) => {
        if (!cancelled) apply(requestedLocale, stored, values);
      });
    }

    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), DICTIONARY_TIMEOUT_MS);
    getInterfaceTranslations(requestedLocale, controller.signal)
      .then(async (dictionary) => {
        dictionaryCache.current.set(requestedLocale, dictionary);
        writeDictionary(requestedLocale, dictionary);
        const values = await curated;
        if (!cancelled) apply(requestedLocale, dictionary, values);
      })
      .catch(async () => {
        if (cancelled) return;
        const current = appliedRef.current;
        if (stored) return;
        if (current && !sameLocale(current.locale, requestedLocale)) {
          // Switching failed: stay on the language that is fully on screen.
          setRequestedLocale(current.locale);
          writeStored(STORAGE_KEY, current.locale);
        } else if (!current) {
          // Nothing received yet: the shipped catalog alone still shows every label in the chosen language.
          const values = await curated;
          if (!cancelled) apply(requestedLocale, NO_VALUES, values);
        }
      })
      .finally(() => window.clearTimeout(timeout));

    return () => {
      cancelled = true;
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [requestedLocale, apply, curatedFor]);

  const refreshDictionary = useCallback(async (changedLocale?: string) => {
    const locale = appliedRef.current?.locale ?? requestedLocale;
    if (changedLocale) {
      // Copies of other languages that inherit the changed one are dropped, so switching to them loads the new values.
      for (const code of [...dictionaryCache.current.keys()]) {
        if (!sameLocale(code, locale) && inheritsFrom(code, changedLocale)) dictionaryCache.current.delete(code);
      }
      forgetDictionary(changedLocale);
      if (!inheritsFrom(locale, changedLocale)) return;
    }
    const dictionary = await getInterfaceTranslations(locale);
    dictionaryCache.current.set(locale, dictionary);
    writeDictionary(locale, dictionary);
    apply(locale, dictionary, await curatedFor(locale));
  }, [requestedLocale, apply, curatedFor]);

  const locale = applied?.locale ?? requestedLocale;
  const t = useMemo(
    () => createTranslate(applied?.dictionary ?? NO_VALUES, locale, {
      curated: applied?.curated ?? NO_VALUES, english: ENGLISH_CATALOG, onMissing: reportMissingKey,
    }),
    [applied, locale],
  );
  const direction = directionForLocale(locale);

  useEffect(() => {
    if (!applied) return;
    document.documentElement.lang = applied.locale;
    document.documentElement.dir = direction;
    document.title = t('app.documentTitle');
    document.querySelector('meta[name="description"]')?.setAttribute('content', t('app.metaDescription'));
  }, [applied, direction, t]);

  const value = useMemo<I18nValue>(
    () => ({
      locale,
      requestedLocale,
      switching: !sameLocale(locale, requestedLocale),
      direction,
      setLocale,
      t,
      available,
      reloadLanguages,
      refreshDictionary,
    }),
    [locale, requestedLocale, direction, setLocale, t, available, reloadLanguages, refreshDictionary]
  );

  if (!applied) {
    const label = BOOT_LABELS[requestedLocale.split(/[-_]/)[0].toLowerCase()] ?? BOOT_LABELS.en;
    return (
      <div className="flex min-h-dvh items-center justify-center" role="status" aria-label={label}>
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-line border-t-brand motion-reduce:animate-none" aria-hidden="true" />
      </div>
    );
  }

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}
