import type { InterfaceLanguageCoverage, InterfaceTranslationJob, InterfaceTranslationOverview } from '../types/api';

export const TRANSLATION_OVERVIEW_PATH = '/api/interface-translations/overview';

/** How often the coverage is reloaded while a machine translation job is queued or running. */
export const TRANSLATION_OVERVIEW_POLL_MS = 15_000;

const FLAGS = ['automatic', 'providerConfigured', 'workerEnabled', 'activeLanguagesAvailable'] as const;

/** The overview endpoint answered with something that is not an overview; the message says what came back instead. */
export class TranslationOverviewContractError extends Error {
  readonly payload: unknown;

  constructor(problem: string, payload: unknown) {
    super(`Unexpected response from GET ${TRANSLATION_OVERVIEW_PATH}: ${problem}`);
    this.name = 'TranslationOverviewContractError';
    this.payload = payload;
  }
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const describe = (value: unknown) => (value === null ? 'null' : Array.isArray(value) ? 'an array' : typeof value);

/**
 * A reference-service without the overview route matches the path as GET /{languageCode} and answers 200 with the
 * dictionary of a language called "overview": a flat map of strings. Naming that case points at the deployment.
 */
function missingLanguagesProblem(payload: Record<string, unknown>): string {
  const keys = Object.keys(payload);
  const problem = `"languages" is ${describe(payload.languages)}, expected an array`;
  if (keys.length > 0 && keys.every((key) => typeof payload[key] === 'string')) {
    return `${problem}; the body is a flat string map (${keys.slice(0, 3).join(', ')}, …), i.e. an interface dictionary. `
      + 'The reference-service behind the gateway does not implement the overview endpoint, so the request reached GET /{languageCode}.';
  }
  return `${problem} (fields present: ${keys.join(', ') || 'none'})`;
}

function parseJob(value: unknown, languageCode: string, payload: unknown): InterfaceTranslationJob | null {
  if (value === null || value === undefined) return null;
  if (!isRecord(value) || typeof value.status !== 'string') {
    throw new TranslationOverviewContractError(`the job of "${languageCode}" has no status`, payload);
  }
  return value as unknown as InterfaceTranslationJob;
}

function parseLanguage(value: unknown, index: number, payload: unknown): InterfaceLanguageCoverage {
  if (!isRecord(value) || typeof value.languageCode !== 'string') {
    throw new TranslationOverviewContractError(`languages[${index}] has no languageCode`, payload);
  }
  return { ...(value as unknown as InterfaceLanguageCoverage), job: parseJob(value.job, value.languageCode, payload) };
}

/** Checks the response of {@link TRANSLATION_OVERVIEW_PATH} against the shape the coverage card renders. */
export function parseTranslationOverview(payload: unknown): InterfaceTranslationOverview {
  if (!isRecord(payload)) throw new TranslationOverviewContractError(`the body is ${describe(payload)}, expected an object`, payload);
  if (!Array.isArray(payload.languages)) throw new TranslationOverviewContractError(missingLanguagesProblem(payload), payload);
  const flag = FLAGS.find((name) => typeof payload[name] !== 'boolean');
  if (flag) throw new TranslationOverviewContractError(`"${flag}" is ${describe(payload[flag])}, expected a boolean`, payload);
  return {
    ...(payload as unknown as InterfaceTranslationOverview),
    languages: payload.languages.map((item, index) => parseLanguage(item, index, payload)),
  };
}

/** True while any language's machine translation job is still waiting for or held by the worker. */
export function hasActiveTranslationJob(overview: Partial<InterfaceTranslationOverview> | null | undefined): boolean {
  const languages: unknown = overview?.languages;
  if (!Array.isArray(languages)) return false;
  return languages.some((item) => {
    const status = isRecord(item) && isRecord(item.job) ? item.job.status : undefined;
    return status === 'PENDING' || status === 'RUNNING';
  });
}

export const translationOverviewRefetchInterval = (overview: Partial<InterfaceTranslationOverview> | null | undefined) =>
  hasActiveTranslationJob(overview) ? TRANSLATION_OVERVIEW_POLL_MS : false;
