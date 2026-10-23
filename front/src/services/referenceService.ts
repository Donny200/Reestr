import type {
  InterfaceTranslationImportResult, InterfaceTranslationJob, InterfaceTranslationOverview, Language, LanguageSearchResult, Region,
} from '../types/api';
import { parseTranslationOverview, TRANSLATION_OVERVIEW_PATH } from '../utils/translationOverview';
import { apiDownload, apiRequest } from './http';

export const getRegions = () => apiRequest<Region[]>('/api/regions');

export const getLanguages = () => apiRequest<Language[]>('/api/languages');

export const getInterfaceTranslations = (languageCode: string, signal?: AbortSignal) =>
  apiRequest<Record<string, string>>(`/api/interface-translations/${encodeURIComponent(languageCode)}`, { signal });

export const searchLanguages = (query: string) =>
  apiRequest<LanguageSearchResult[]>('/api/languages/search', { query: { q: query } });

export const addLanguage = (code: string) => apiRequest<Language>('/api/languages', { method: 'POST', body: { code } });

export const deleteLanguage = (id: number) => apiRequest<void>(`/api/languages/${id}`, { method: 'DELETE' });

/** Rejects with a TranslationOverviewContractError when the server answers with anything but an overview. */
export const getInterfaceTranslationOverview = async (): Promise<InterfaceTranslationOverview> =>
  parseTranslationOverview(await apiRequest<unknown>(TRANSLATION_OVERVIEW_PATH));

/** Queues (or retries) filling the language's missing interface entries; the server's worker calls Azure later. */
export const queueInterfaceMachineTranslation = (languageCode: string) =>
  apiRequest<InterfaceTranslationJob>(`/api/interface-translations/${encodeURIComponent(languageCode)}/machine-translation`, { method: 'POST' });

/** Downloads the JSON backup of one language's stored translations, or of every language without a code. */
export const exportInterfaceTranslations = (languageCode?: string) =>
  apiDownload(languageCode ? `/api/interface-translations/${encodeURIComponent(languageCode)}/export` : '/api/interface-translations/export');

/** Restores a backup made by exportInterfaceTranslations; values people edited here are kept. */
export const importInterfaceTranslations = (languageCode: string, backup: unknown) =>
  apiRequest<InterfaceTranslationImportResult>(`/api/interface-translations/${encodeURIComponent(languageCode)}/import`, {
    method: 'POST', body: backup,
  });
