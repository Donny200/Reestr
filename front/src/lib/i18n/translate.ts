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

export function createTranslate(dictionary: Dictionary, locale: string): Translate {
  const lookup = (key: string, params: TranslateParams | undefined): string | undefined => {
    const count = params?.count;
    if (typeof count === 'number') {
      const plural = dictionary[`${key}_${rulesFor(locale).select(count)}`] || dictionary[`${key}_other`];
      if (plural) return plural;
    }
    return dictionary[key] || undefined;
  };
  const translate = (key: string, fallbackOrParams?: string | TranslateParams, maybeParams?: TranslateParams): string => {
    const fallback = typeof fallbackOrParams === 'string' ? fallbackOrParams : undefined;
    const params = typeof fallbackOrParams === 'object' ? fallbackOrParams : maybeParams;
    return interpolate(lookup(key, params) ?? fallback ?? key, params, locale);
  };
  return Object.assign(translate, { locale }) as Translate;
}
