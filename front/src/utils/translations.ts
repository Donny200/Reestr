import type { TranslatedText } from '../types/api';

type Translations = Record<string, TranslatedText> | null | undefined;

export function localizedText(original: string, translations: Translations, locale: string): string;
export function localizedText(original: string | null | undefined, translations: Translations, locale: string): string | null | undefined;
export function localizedText(original: string | null | undefined, translations: Translations, locale: string): string | null | undefined {
  const translated = translations?.[locale.toLowerCase()]?.text?.trim();
  return translated || original;
}
