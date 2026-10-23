import { createContext, useContext } from 'react';
import type { Translate } from '../lib/i18n/translate';

export type { Translate, TranslateParams } from '../lib/i18n/translate';

export interface InterfaceLanguage {
  code: string;
  label: string;
}

export interface I18nValue {
  /** Locale of the dictionary on screen; use it for content, formatting and lookups. */
  locale: string;
  /** Locale the visitor chose; differs from `locale` only while its dictionary is loading. */
  requestedLocale: string;
  switching: boolean;
  direction: 'ltr' | 'rtl';
  setLocale: (locale: string) => void;
  t: Translate;
  available: InterfaceLanguage[];
  reloadLanguages: () => Promise<void>;
  /**
   * Fetches the current dictionary again (after translations were edited or machine-translated). With a locale, only
   * when the dictionary on screen inherits from it (`fr` for `fr-FR`); other cached copies of that locale are dropped.
   */
  refreshDictionary: (changedLocale?: string) => Promise<void>;
}

export const I18nContext = createContext<I18nValue | null>(null);

export function useI18n(): I18nValue {
  const context = useContext(I18nContext);
  if (!context) throw new Error('useI18n must be used inside I18nProvider');
  return context;
}
