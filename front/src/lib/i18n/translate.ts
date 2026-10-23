/** Values substituted into `{name}` placeholders. `count` also selects the plural form. */
export type TranslateParams = Record<string, string | number>;

/**
 * Looks up an interface string. Forms: `t(key)`, `t(key, fallback)`, `t(key, params)`, `t(key, fallback, params)`.
 * With a numeric `count` parameter the CLDR plural form `${key}_${category}` of the current locale is used
 * (`_one`, `_few`, `_many`, `_other`…), falling back to `${key}_other` and then `key`.
 * `locale` is the locale of the dictionary the function reads from.
 */
export interface Translate {
  (key: string, fallbackOrParams?: string | TranslateParams, params?: TranslateParams): string;
  readonly locale: string;
}

export type Dictionary = Readonly<Record<string, string>>;

const PLACEHOLDER = /\{([A-Za-z0-9_.]+)\}/g;
const pluralRules = new Map<string, Intl.PluralRules>();

function rulesFor(locale: string): Intl.PluralRules {
  let rules = pluralRules.get(locale);
  if (!rules) {
    try {
      rules = new Intl.PluralRules(locale);
    } catch {
      rules = new Intl.PluralRules('en');
    }
    pluralRules.set(locale, rules);
  }
  return rules;
}

/** Replaces `{name}` with the parameter; unknown placeholders stay visible so a missing value is noticed. */
export function interpolate(template: string, params: TranslateParams | undefined, locale: string): string {
  if (!params) return template;
  return template.replace(PLACEHOLDER, (match, name: string) => {
    const value = params[name];
    if (value === undefined) return match;
    return typeof value === 'number' ? formatNumber(value, locale) : value;
  });
}

const numberFormats = new Map<string, Intl.NumberFormat>();

export function formatNumber(value: number, locale: string): string {
  let format = numberFormats.get(locale);
  if (!format) {
    try {
      format = new Intl.NumberFormat(locale);
    } catch {
      format = new Intl.NumberFormat('en');
    }
    numberFormats.set(locale, format);
  }
  return format.format(value);
}

/**
 * Layers under the server dictionary, used only where it has no value of its own:
 * - `curated`: the shipped catalog of the locale's same-script chain (`fr-FR` → `fr`; `uz-Cyrl` has none), so a server
 *   that is older than this build, unreachable, or still translating never leaves a label in English or as a key;
 * - `english`: the shipped English source, the last real text.
 * The server answers with its English base for keys the locale lacks; such a value (equal to the English source) gives
 * way to a curated translation.
 */
export interface TranslateFallbacks {
  curated?: Dictionary;
  english?: Dictionary;
  /** Called once per lookup of a key that no layer defines (a catalog bug; the checks should have caught it). */
  onMissing?: (key: string) => void;
}

const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/;

/** Readable text for a key nothing defines: `nav.appointments` → "Appointments". Never shown when the catalog is complete. */
export function humanizeKey(key: string): string {
  const segments = key.replace(PLURAL_SUFFIX, '').split('.').filter(Boolean);
  const last = segments[segments.length - 1] ?? key;
  const words = last.replace(/[_-]+/g, ' ').replace(/([a-z0-9])([A-Z])/g, '$1 $2').trim().toLowerCase();
  return words ? words[0].toUpperCase() + words.slice(1) : key;
}

export function createTranslate(dictionary: Dictionary, locale: string, fallbacks: TranslateFallbacks = {}): Translate {
  const { curated = {}, english = {}, onMissing } = fallbacks;
  const valueOf = (name: string): string | undefined => {
    const served = dictionary[name] || undefined;
    const own = curated[name] || undefined;
    if (served && !(own && served === english[name] && own !== served)) return served;
    return own;
  };
  const lookup = (key: string, params: TranslateParams | undefined, last: (name: string) => string | undefined) => {
    const count = params?.count;
    const names = typeof count === 'number' ? [`${key}_${rulesFor(locale).select(count)}`, `${key}_other`, key] : [key];
    for (const name of names) {
      const value = last(name);
      if (value) return value;
    }
    return undefined;
  };
  const translate = (key: string, fallbackOrParams?: string | TranslateParams, maybeParams?: TranslateParams): string => {
    const fallback = typeof fallbackOrParams === 'string' ? fallbackOrParams : undefined;
    const params = typeof fallbackOrParams === 'object' ? fallbackOrParams : maybeParams;
    let text = lookup(key, params, valueOf) ?? fallback ?? lookup(key, params, (name) => english[name] || undefined);
    if (text === undefined) {
      onMissing?.(key);
      text = humanizeKey(key);
    }
    return interpolate(text, params, locale);
  };
  return Object.assign(translate, { locale }) as Translate;
}
