import type { CatalogFunction, TranslatedText } from '../types/api';
import { INSTRUCTION_FIELDS } from './instructions';
import { buildSearchIndex, matchesSearch, searchTerms as terms, type SearchIndex } from './search';

/** Query words in canonical form (see utils/search.ts); the locale no longer changes the result. */
export function searchTerms(query: string, locale?: string): string[] {
  void locale;
  return terms(query);
}

function allTexts(original: string | null | undefined, translations: Record<string, TranslatedText> | undefined): string[] {
  return [original ?? '', ...Object.values(translations ?? {}).map((value) => value?.text ?? '')];
}

/**
 * Everything a visitor may type to find the service, in every stored language: name, description, requirements,
 * category, the six instruction fields with their translations and whatever extra labels the caller adds
 * (organization and localized category names).
 */
export function serviceHaystack(record: CatalogFunction, locale?: string, extra: (string | null | undefined)[] = []): SearchIndex {
  void locale;
  const values = [
    ...allTexts(record.name, record.nameTranslations),
    ...allTexts(record.description, record.descriptionTranslations),
    record.requirements,
    record.category,
    ...INSTRUCTION_FIELDS.flatMap((field) => [
      record.instructions?.[field.key],
      ...Object.values(record.instructionTranslations?.[field.key] ?? {}).map((value) => value?.text ?? ''),
    ]),
    ...extra,
  ];
  return buildSearchIndex(values);
}

export function matchesTerms(haystack: SearchIndex, searchWords: string[]): boolean {
  return matchesSearch(haystack, searchWords);
}
