import type { CatalogFunction, TranslatedText } from '../types/api';
import { INSTRUCTION_FIELDS } from './instructions';
import { buildSearchIndex, isStopWord, matchForm, searchTerms, termMatch, type SearchIndex } from './search';
import { localizedText } from './translations';

/**
 * Ranking of the public service finder on top of the canonical search of utils/search.ts (same normalization,
 * transliteration, synonyms and typo tolerance). Each query word is looked up field by field in this order and the
 * first field where it matches decides its rank:
 *
 *   exact service name > name in the visitor's language > name in another stored language > name via a synonym or
 *   typo > description > organization > instructions, requirements and category.
 *
 * A service ranks by its weakest required word (all required words must match somewhere), then by the sum of all
 * word ranks, then by name. Stop words ("how", "get", "как", …) are optional. The fields that matched are returned so
 * the page can say why a service is listed.
 */
export type MatchField = 'exactName' | 'name' | 'translatedName' | 'synonym' | 'description' | 'organization' | 'instructions';

export const MATCH_ORDER: MatchField[] = ['exactName', 'name', 'translatedName', 'synonym', 'description', 'organization', 'instructions'];

export interface RankedService {
  item: CatalogFunction;
  /** Rank of the weakest required word (index in MATCH_ORDER); 0 is best. */
  rank: number;
  score: number;
  /** Fields that matched, best first, without duplicates. */
  reasons: MatchField[];
}

export interface RankingContext {
  locale: string;
  organizationNames?: (organizationId: number) => (string | null | undefined)[];
  categoryNames?: (categoryId: number | null | undefined) => (string | null | undefined)[];
}

interface ServiceIndexes {
  shownName: SearchIndex;
  otherNames: SearchIndex;
  allNames: SearchIndex;
  description: SearchIndex;
  organization: SearchIndex;
  instructions: SearchIndex;
  nameForms: string[];
}

function texts(original: string | null | undefined, translations: Record<string, TranslatedText> | undefined): string[] {
  return [original ?? '', ...Object.values(translations ?? {}).map((value) => value?.text ?? '')].filter(Boolean);
}

function buildIndexes(item: CatalogFunction, context: RankingContext): ServiceIndexes {
  const shown = localizedText(item.name, item.nameTranslations, context.locale);
  const names = texts(item.name, item.nameTranslations);
  return {
    shownName: buildSearchIndex([shown]),
    otherNames: buildSearchIndex(names.filter((name) => name !== shown)),
    allNames: buildSearchIndex(names),
    description: buildSearchIndex(texts(item.description, item.descriptionTranslations)),
    organization: buildSearchIndex(context.organizationNames?.(item.organizationId) ?? []),
    instructions: buildSearchIndex([
      item.requirements,
      item.category,
      ...(context.categoryNames?.(item.categoryId) ?? []),
      ...INSTRUCTION_FIELDS.flatMap((field) => [
        item.instructions?.[field.key],
        ...Object.values(item.instructionTranslations?.[field.key] ?? {}).map((value) => value?.text ?? ''),
      ]),
    ]),
    nameForms: names.map(matchForm),
  };
}

/** Indexes depend only on the service and the context, so each pair is built once (intents rank every service many times). */
const indexCache = new WeakMap<RankingContext, WeakMap<CatalogFunction, ServiceIndexes>>();

function indexes(item: CatalogFunction, context: RankingContext): ServiceIndexes {
  let byItem = indexCache.get(context);
  if (!byItem) {
    byItem = new WeakMap();
    indexCache.set(context, byItem);
  }
  let index = byItem.get(item);
  if (!index) {
    index = buildIndexes(item, context);
    byItem.set(item, index);
  }
  return index;
}

/** Best field of one word, or null when it matches nowhere. */
function wordRank(index: ServiceIndexes, term: string): number | null {
  if (termMatch(index.shownName, term) === 'direct') return 1;
  if (termMatch(index.otherNames, term) === 'direct') return 2;
  if (termMatch(index.allNames, term) !== null) return 3;
  if (termMatch(index.description, term) !== null) return 4;
  if (termMatch(index.organization, term) !== null) return 5;
  if (termMatch(index.instructions, term) !== null) return 6;
  return null;
}

export function rankService(item: CatalogFunction, query: string, context: RankingContext): RankedService | null {
  const terms = searchTerms(query);
  if (terms.length === 0) return { item, rank: 0, score: 0, reasons: [] };
  const index = indexes(item, context);
  const required = terms.filter((term) => !isStopWord(term));
  const mustMatch = new Set(required.length > 0 ? required : terms);
  const ranks: number[] = [];
  const reasons = new Set<number>();
  for (const term of terms) {
    const rank = wordRank(index, term);
    if (rank === null) {
      if (mustMatch.has(term)) return null;
      continue;
    }
    if (mustMatch.has(term)) ranks.push(rank);
    reasons.add(rank);
  }
  const phrase = matchForm(query);
  const exact = index.nameForms.includes(phrase) || index.nameForms.includes(required.join(' '));
  const rank = exact ? 0 : Math.max(...ranks);
  if (exact) reasons.add(0);
  const ordered = [...reasons].sort((a, b) => a - b).map((value) => MATCH_ORDER[value]);
  return { item, rank, score: rank * 100 + ranks.reduce((sum, value) => sum + value, 0), reasons: ordered };
}

/** Matching services, best first. Without query words every service is returned in name order. */
export function rankServices(items: CatalogFunction[], query: string, context: RankingContext): RankedService[] {
  const name = (item: CatalogFunction) => localizedText(item.name, item.nameTranslations, context.locale);
  return items
    .map((item) => rankService(item, query, context))
    .filter((ranked): ranked is RankedService => ranked !== null)
    .sort((a, b) => a.rank - b.rank || a.score - b.score || name(a.item).localeCompare(name(b.item), context.locale));
}

/**
 * Guided path: everyday intents instead of internal categories. Each intent lists plain words that go through the
 * same search (synonyms included); a service belongs to an intent when any of its words matches the service.
 */
export interface FinderIntent {
  key: string;
  labelKey: string;
  label: string;
  words: string[];
}

export const FINDER_INTENTS: FinderIntent[] = [
  { key: 'certificate', labelKey: 'finder.intent.certificate', label: 'Get a certificate or an extract', words: ['certificate', 'extract'] },
  { key: 'register', labelKey: 'finder.intent.register', label: 'Register a birth, marriage, property or other event', words: ['registration', 'birth', 'marriage', 'real estate'] },
  { key: 'replace', labelKey: 'finder.intent.replace', label: 'Replace, renew or duplicate a document', words: ['replace', 'renewal', 'duplicate'] },
  { key: 'identity', labelKey: 'finder.intent.identity', label: 'Passport or ID card', words: ['passport', 'id card'] },
  { key: 'permit', labelKey: 'finder.intent.permit', label: 'Get a permit or licence', words: ['license'] },
  { key: 'business', labelKey: 'finder.intent.business', label: 'Start or run a business, taxes', words: ['business', 'tax'] },
  { key: 'legal', labelKey: 'finder.intent.legal', label: 'Notary, court and legal documents', words: ['notary', 'court', 'power of attorney', 'apostille', 'criminal record'] },
  { key: 'pension', labelKey: 'finder.intent.pension', label: 'Pension and benefits', words: ['pension'] },
];

export function matchesIntent(item: CatalogFunction, intent: FinderIntent, context: RankingContext): boolean {
  return intent.words.some((word) => rankService(item, word, context) !== null);
}
