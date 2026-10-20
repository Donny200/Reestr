import { createContext, useContext } from 'react';

export interface InterfaceLanguage {
  code: string;
  label: string;
}

export interface I18nValue {
  locale: string;
  setLocale: (locale: string) => void;
  t: (key: string, fallback?: string) => string;
  available: InterfaceLanguage[];
  reloadLanguages: () => Promise<void>;
}

export const I18nContext = createContext<I18nValue | null>(null);

export function useI18n(): I18nValue {
  const context = useContext(I18nContext);
  if (!context) throw new Error('useI18n must be used inside I18nProvider');
  return context;
}
