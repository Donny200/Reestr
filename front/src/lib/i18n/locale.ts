const RTL_LANGUAGES = new Set(['ar', 'ckb', 'dv', 'fa', 'he', 'ku', 'ps', 'sd', 'ug', 'ur', 'yi']);
const RTL_SCRIPTS = new Set(['Adlm', 'Arab', 'Hebr', 'Mand', 'Nkoo', 'Rohg', 'Samr', 'Syrc', 'Thaa']);
/** Usual script of languages written in several scripts; a tag in another script does not inherit across it. */
const DEFAULT_SCRIPTS: Record<string, string> = {
  az: 'Latn', bs: 'Latn', ha: 'Latn', iu: 'Cans', kk: 'Cyrl', ks: 'Arab', ku: 'Latn', ky: 'Cyrl', mn: 'Cyrl',
  ms: 'Latn', pa: 'Guru', sd: 'Arab', sr: 'Cyrl', tg: 'Cyrl', tk: 'Latn', ug: 'Arab', uz: 'Latn', yue: 'Hant', zh: 'Hans',
};

interface TagParts {
  language: string;
  script?: string;
  region?: string;
}

function parts(locale: string): TagParts {
  const [language = '', ...rest] = locale.replace(/_/g, '-').split('-');
  const script = rest.find((part) => /^[A-Za-z]{4}$/.test(part));
  const region = rest.find((part) => /^([A-Za-z]{2}|\d{3})$/.test(part));
  return {
    language: language.toLowerCase(),
    script: script ? `${script[0].toUpperCase()}${script.slice(1).toLowerCase()}` : undefined,
    region: region?.toUpperCase(),
  };
}

export function directionForLocale(locale: string): 'ltr' | 'rtl' {
  const { language, script } = parts(locale);
  if (script) return RTL_SCRIPTS.has(script) ? 'rtl' : 'ltr';
  return RTL_LANGUAGES.has(language) ? 'rtl' : 'ltr';
}

/**
 * Lower-case lookup chain for content translations, most specific first: `uz-Latn-UZ` gives `uz-latn-uz`, `uz-latn`,
 * `uz`. A script other than the language's usual one stops the chain, so `uz-Cyrl` never shows Latin Uzbek content.
 */
export function contentFallbacks(locale: string): string[] {
  const { language, script, region } = parts(locale);
  const chain = [locale.replace(/_/g, '-').toLowerCase()];
  if (!language) return chain;
  const add = (value: string) => {
    if (!chain.includes(value)) chain.push(value);
  };
  if (script && region) add(`${language}-${script}-${region}`.toLowerCase());
  if (script) add(`${language}-${script}`.toLowerCase());
  const usual = DEFAULT_SCRIPTS[language];
  if (!script || !usual || usual === script) {
    if (region) add(`${language}-${region}`.toLowerCase());
    add(language);
  }
  return chain;
}

/** Same language, ignoring case and `_` vs `-`. */
export function sameLocale(left: string, right: string): boolean {
  return left.replace(/_/g, '-').toLowerCase() === right.replace(/_/g, '-').toLowerCase();
}

const displayNames = new Map<string, Intl.DisplayNames | null>();

/** Name of a language in the interface language (`fr` shown as "французский" in Russian), or the tag itself. */
export function languageDisplayName(code: string, uiLocale: string): string {
  let names = displayNames.get(uiLocale);
  if (names === undefined) {
    try {
      names = new Intl.DisplayNames([uiLocale, 'en'], { type: 'language' });
    } catch {
      names = null;
    }
    displayNames.set(uiLocale, names);
  }
  try {
    return names?.of(code) ?? code;
  } catch {
    return code;
  }
}
