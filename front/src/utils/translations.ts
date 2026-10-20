import type { TranslatedText } from '../types/api';
import { contentFallbacks } from '../lib/i18n/locale';

type Translations = Record<string, TranslatedText> | null | undefined;

/**
 * Translation of a content field for the interface locale, else the original text. Variants fall back to their parent
 * language (`fr-FR` uses `fr`); a different script does not (`uz-Cyrl` never shows Latin Uzbek).
 */
export function localizedText(original: string, translations: Translations, locale: string): string;
export function localizedText(original: string | null | undefined, translations: Translations, locale: string): string | null | undefined;
export function localizedText(original: string | null | undefined, translations: Translations, locale: string): string | null | undefined {
  if (translations) {
    for (const code of contentFallbacks(locale)) {
      const translated = translations[code]?.text?.trim();
      if (translated) return translated;
    }
  }
  return original;
}
