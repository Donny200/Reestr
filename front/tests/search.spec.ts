import { test, expect } from '@playwright/test';
import { buildSearchIndex, editDistance, matchForm, matchesSearch, normalizeSearchText, searchTerms, transliterateCyrillic } from '../src/utils/search';
import { matchesTerms, serviceHaystack } from '../src/utils/finder';
import { card } from './fixtures';
import type { CatalogFunction } from '../src/types/api';

/** Pure functions of the in-browser search engine; no page is needed. */
const passport: CatalogFunction = {
  ...card(1, 'PUBLISHED'),
  organizationId: 10,
  name: 'Passport renewal',
  description: 'Renew a biometric passport',
  nameTranslations: { ru: { text: 'Продление паспорта', source: 'human' as const }, uz: { text: 'Pasportni yangilash', source: 'human' as const }, fr: { text: 'Renouvellement du passeport', source: 'human' as const } },
  descriptionTranslations: { uz: { text: 'Biometrik pasportni yangilash', source: 'human' as const } },
  instructions: { whoCanUse: null, steps: 'Fill in the form', requiredDocuments: "O'zbekiston fuqarosining pasporti", whereHowToApply: null, processingTime: null, fee: null },
};
const index = serviceHaystack(passport, 'en', ['Adliya vazirligi', 'Министерство юстиции', 'Legal']);

test('normalization folds case, whitespace, punctuation, diacritics and apostrophe variants', () => {
  expect(normalizeSearchText('  Renouvellement,   du Passeport!  ')).toBe('renouvellement du passeport');
  expect(normalizeSearchText('Café-Crème')).toBe('cafe creme');
  expect(matchForm('Oʻzbekiston')).toBe(matchForm("O'zbekiston"));
  expect(matchForm('O‘zbekiston')).toBe('ozbekiston');
  expect(matchForm('Gʻuvohnoma')).toBe('guvohnoma');
  expect(searchTerms('a passport, renewal!')).toEqual(['passport', 'renewal']);
});

test('uzbek cyrillic and latin scripts meet in the same canonical form', () => {
  expect(transliterateCyrillic('ўзбекистон')).toBe("o'zbekiston");
  expect(matchForm('Ўзбекистон фуқаросининг паспорти')).toBe(matchForm("O'zbekiston fuqarosining pasporti"));
  expect(matchForm('Ғувоҳнома')).toBe("g'uvohnoma".replace("'", ''));
  expect(matchForm('янгилаш')).toBe('yangilash');
  expect(matchForm('Паспорт')).toBe('pasport');
});

test('queries in every supported language and either script find the same service', () => {
  for (const query of ['passport renewal', 'Продление паспорта', 'Pasportni yangilash', 'Паспортни янгилаш', 'renouvellement passeport', 'ўзбекистон', "o'zbekiston", 'ozbekiston', 'adliya', 'юстиции', 'legal']) {
    expect(matchesTerms(index, searchTerms(query)), query).toBe(true);
  }
});

test('synonyms and bounded typo tolerance help, unrelated words and short typos do not', () => {
  expect(matchesSearch(index, searchTerms('pasport'))).toBe(true);
  expect(matchesSearch(buildSearchIndex(['Nikoh qayd etish']), searchTerms('marriage'))).toBe(true);
  expect(matchesSearch(buildSearchIndex(['Soliq qarzdorligi']), searchTerms('nalog'))).toBe(true);
  expect(matchesSearch(index, searchTerms('pasaport'))).toBe(true);
  expect(matchesSearch(index, searchTerms('renewel'))).toBe(true);
  expect(matchesSearch(index, searchTerms('form'))).toBe(true);
  expect(matchesSearch(index, searchTerms('frm'))).toBe(false);
  expect(matchesSearch(index, searchTerms('driving licence'))).toBe(false);
  expect(matchesSearch(index, searchTerms('passport apostille'))).toBe(false);
  expect(editDistance('passport', 'pasport')).toBe(1);
  expect(editDistance('passport', 'passprot')).toBe(1);
  expect(editDistance('passport', 'visa')).toBe(2);
});

test('empty or single-character queries match everything and no-match queries match nothing', () => {
  expect(searchTerms('')).toEqual([]);
  expect(searchTerms('a')).toEqual([]);
  expect(matchesSearch(index, [])).toBe(true);
  expect(matchesSearch(index, searchTerms('xyzzy qwerty'))).toBe(false);
});
