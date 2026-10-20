/**
 * Multilingual catalog search that runs entirely in the browser over public data.
 *
 * Every text (service names, descriptions, instructions, organization and category names in every stored
 * language) and every query goes through the same canonical form, so a query in Uzbek Cyrillic finds Uzbek Latin
 * content, apostrophe variants collapse and accents do not matter. The rules are documented in
 * docs/public-search-and-visitor-tools.md; keep that file in sync when changing anything here.
 */

/** Apostrophe-like characters that Uzbek Latin text uses interchangeably for oʻ, gʻ and the tutuq belgisi. */
const APOSTROPHES = /[‘’ʻʼʹʽ´`′']/g;

/** Uzbek Cyrillic first, then the remaining Russian letters; digraphs are expanded, hard/soft signs become an apostrophe or nothing. */
const CYRILLIC_TO_LATIN: Record<string, string> = {
  а: 'a', б: 'b', в: 'v', г: 'g', ғ: "g'", д: 'd', е: 'e', ё: 'yo', ж: 'j', з: 'z', и: 'i', й: 'y', к: 'k', қ: 'q', л: 'l',
  м: 'm', н: 'n', о: 'o', ў: "o'", п: 'p', р: 'r', с: 's', т: 't', у: 'u', ф: 'f', х: 'x', ҳ: 'h', ц: 'ts', ч: 'ch', ш: 'sh',
  щ: 'sh', ъ: "'", ы: 'i', ь: '', э: 'e', ю: 'yu', я: 'ya', ї: 'yi', і: 'i', є: 'ye',
};

/**
 * Small, hand-maintained synonym groups in canonical form (lowercase Latin, no apostrophes). A query term equal to
 * any member also matches content containing any other member. Keep groups short and unambiguous.
 */
export const SEARCH_SYNONYMS: readonly (readonly string[])[] = [
  ['passport', 'pasport'],
  ['id card', 'id karta', 'shaxsiy guvohnoma', 'udostoverenie lichnosti'],
  ['marriage', 'nikoh', 'brak', 'zags', 'fhdyo', 'mariage'],
  ['birth', 'tugilganlik', 'rojdenie', 'rojdeniya'],
  ['certificate', 'guvohnoma', 'spravka', 'malumotnoma', 'svidetelstvo', 'certificat', 'attestation'],
  ['extract', 'vypiska', 'kochirma', 'extrait'],
  ['business', 'biznes', 'tadbirkorlik', 'predprinimatelstvo'],
  ['license', 'licence', 'litsenziya', 'ruxsatnoma', 'razreshenie'],
  ['tax', 'soliq', 'nalog', 'nalogovaya'],
  ['registration', 'register', 'royxat', 'royxatga olish', 'qayd etish', 'registratsiya', 'registraciya', 'zaregistrirovat', 'enregistrement', 'enregistrer', 'inscription'],
  ['duplicate', 'dublikat', 'nusxa'],
  ['renewal', 'renew', 'yangilash', 'uzaytirish', 'prodlenie', 'renouvellement'],
  ['replace', 'replacement', 'almashtirish', 'zamena', 'zamenit', 'obmen', 'remplacement', 'remplacer'],
  ['apostille', 'apostil'],
  ['notary', 'notarius', 'notarial', 'notarialnoe'],
  ['real estate', 'kochmas mulk', 'nedvijimost', 'uy joy'],
  ['criminal record', 'sudlanganlik', 'sudimost'],
  ['archive', 'arxiv', 'arhiv'],
  ['power of attorney', 'ishonchnoma', 'doverennost'],
  ['pension', 'pensiya', 'nafaqa'],
  ['court', 'sud'],
];

/**
 * Filler words of plain-language questions ("how do I get a certificate", "как получить справку"). They are optional:
 * a stop word that matches nothing does not exclude a service, one that matches still counts.
 */
export const STOP_WORDS: ReadonlySet<string> = new Set([
  'a', 'an', 'the', 'to', 'for', 'of', 'in', 'on', 'my', 'me', 'i', 'how', 'do', 'can', 'get', 'want', 'need', 'please', 'about', 'and', 'or',
  'kak', 'mne', 'nujno', 'hochu', 'dlya', 'poluchit', 'gde', 'i', 'na', 'po',
  'qanday', 'kerak', 'uchun', 'menga', 'olish', 'qayerda', 'va',
  'le', 'la', 'les', 'un', 'une', 'des', 'du', 'de', 'pour', 'comment', 'obtenir', 'mon', 'ma', 'et',
  // Generic nouns: "replace a document" is about replacing, whatever the document is called on the card.
  'document', 'documents', 'hujjat', 'hujjatni', 'hujjatlar', 'dokument', 'dokumenta', 'dokumenty',
].map((word) => word.replace(/'/g, '')));

const MIN_TERM_LENGTH = 2;
const FUZZY_MIN_LENGTH = 5;

/** Lowercase, apostrophes unified, Cyrillic transliterated to Latin, diacritics removed, whitespace collapsed. */
export function normalizeSearchText(value: string | null | undefined): string {
  if (!value) return '';
  let text = value.normalize('NFC').replace(/İ/g, 'i').toLowerCase().replace(APOSTROPHES, "'");
  text = transliterateCyrillic(text);
  text = text.normalize('NFKD').replace(/[̀-ͯ]/g, '');
  text = text.replace(/[^\p{L}\p{N}']+/gu, ' ').replace(/'+/g, "'").trim();
  return text;
}

export function transliterateCyrillic(text: string): string {
  let out = '';
  for (const char of text) {
    const mapped = CYRILLIC_TO_LATIN[char];
    out += mapped === undefined ? char : mapped;
  }
  return out;
}

/** Form used for matching: canonical text without apostrophes, so "o'zbek", "oʻzbek" and "ozbek" are equal. */
export function matchForm(value: string | null | undefined): string {
  return normalizeSearchText(value).replace(/'/g, '').replace(/\s+/g, ' ').trim();
}

export interface SearchIndex {
  /** Full matchable text joined by single spaces. */
  text: string;
  /** Distinct words of the text, for bounded typo tolerance. */
  tokens: string[];
}

export function buildSearchIndex(values: (string | null | undefined)[]): SearchIndex {
  const text = values.map(matchForm).filter(Boolean).join(' ');
  return { text, tokens: Array.from(new Set(text.split(' ').filter(Boolean))) };
}

/** Query words in match form; single characters are ignored. */
export function searchTerms(query: string): string[] {
  return matchForm(query).split(' ').filter((term) => term.length >= MIN_TERM_LENGTH);
}

const synonymIndex = new Map<string, readonly string[]>();
for (const group of SEARCH_SYNONYMS) {
  for (const member of group) synonymIndex.set(matchForm(member), group.map(matchForm));
}

/**
 * Synonym group of a query word: exact member, a member the word starts with (inflected forms), or, for longer words,
 * a member within one edit, so "nikohni", "royxatga" or "spravku" still reach their groups.
 */
export function synonymsOf(term: string): readonly string[] {
  const exact = synonymIndex.get(term);
  if (exact) return exact;
  // Suffixes of Uzbek and Russian ("nikohni", "royxatga", "pasportni") keep the stem: a word that starts with a
  // single-word member of at least four letters belongs to its group (the longest such member wins).
  let stem: string | undefined;
  for (const member of synonymIndex.keys()) {
    if (!member.includes(' ') && member.length >= 4 && term.startsWith(member) && (!stem || member.length > stem.length)) stem = member;
  }
  if (stem) return synonymIndex.get(stem) ?? [];
  if (term.length < FUZZY_MIN_LENGTH) return [];
  for (const [member, group] of synonymIndex) {
    if (!member.includes(' ') && member.length >= FUZZY_MIN_LENGTH && editDistance(member, term) <= 1) return group;
  }
  return [];
}

/** Optimal string alignment distance capped at 2, enough to decide whether two words are within one edit. */
export function editDistance(a: string, b: string): number {
  if (a === b) return 0;
  if (Math.abs(a.length - b.length) > 1) return 2;
  const previous = Array.from({ length: b.length + 1 }, (_, index) => index);
  let beforePrevious: number[] = [];
  let current: number[] = [];
  for (let i = 1; i <= a.length; i++) {
    current = [i];
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      let value = Math.min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) value = Math.min(value, beforePrevious[j - 2] + 1);
      current[j] = value;
    }
    beforePrevious = previous.slice();
    previous.splice(0, previous.length, ...current);
  }
  return Math.min(previous[b.length], 2);
}

/** How a query word matched a text: literally, through a synonym group, or within one typo. */
export type TermMatch = 'direct' | 'synonym' | 'typo';

export function termMatch(index: SearchIndex, term: string): TermMatch | null {
  if (index.text.includes(term)) return 'direct';
  if (synonymsOf(term).some((alternative) => alternative !== term && index.text.includes(alternative))) return 'synonym';
  if (term.length >= FUZZY_MIN_LENGTH && index.tokens.some((token) => token.length >= FUZZY_MIN_LENGTH && editDistance(token, term) <= 1)) {
    return 'typo';
  }
  return null;
}

export function isStopWord(term: string): boolean {
  return STOP_WORDS.has(term);
}

/**
 * Every query term must match (as substring, synonym or, for longer words, within one typo). Stop words are optional,
 * but a query made only of stop words still has to match.
 */
export function matchesSearch(index: SearchIndex, terms: string[]): boolean {
  const required = terms.filter((term) => !isStopWord(term));
  return (required.length > 0 ? required : terms).every((term) => termMatch(index, term) !== null);
}
