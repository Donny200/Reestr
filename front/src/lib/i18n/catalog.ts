import english from '../../../../reference-service/src/main/resources/i18n/interface/en.json';
import { contentFallbacks } from './locale';
import type { Dictionary } from './translate';

/**
 * The shipped interface catalog (reference-service/src/main/resources/i18n/interface), the same files the server seeds
 * its database from. English is part of the bundle; each curated language is a separate chunk loaded when it is
 * selected. The server dictionary always takes precedence; see TranslateFallbacks.
 */
export const ENGLISH_CATALOG: Dictionary = english;

const loaders = import.meta.glob<Dictionary>(
  ['../../../../reference-service/src/main/resources/i18n/interface/*.json', '!../../../../reference-service/src/main/resources/i18n/interface/en.json'],
  { import: 'default' },
);
const byCode = new Map(Object.entries(loaders).map(([file, load]) => [file.replace(/^.*\//, '').replace(/\.json$/, '').toLowerCase(), load]));
const loaded = new Map<string, Promise<Dictionary>>();

function load(code: string): Promise<Dictionary> {
  let promise = loaded.get(code);
  if (!promise) {
    const loader = byCode.get(code);
    promise = loader ? loader().catch(() => ({})) : Promise.resolve({});
    loaded.set(code, promise);
  }
  return promise;
}

/**
 * Curated values for a locale: its same-script chain merged from the least specific level (`fr-FR` → `fr`, then
 * `fr-fr` if shipped). `uz-Cyrl` gets nothing from Latin `uz`. English-based tags get nothing: English is the base.
 * Never rejects.
 */
export async function loadCuratedCatalog(locale: string): Promise<Dictionary> {
  const chain = contentFallbacks(locale).filter((code) => code.split('-')[0] !== 'en').reverse();
  const levels = await Promise.all(chain.map(load));
  return Object.assign({}, ...levels) as Dictionary;
}
