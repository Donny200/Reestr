import type { InterfaceTranslationJob, InterfaceTranslationOverview, Language, LanguageSearchResult, Region } from '../types/api';
import { apiRequest } from './http';

export const getRegions = () => apiRequest<Region[]>('/api/regions');

export const getLanguages = () => apiRequest<Language[]>('/api/languages');

export const getInterfaceTranslations = (languageCode: string, signal?: AbortSignal) =>
  apiRequest<Record<string, string>>(`/api/interface-translations/${encodeURIComponent(languageCode)}`, { signal });

export const searchLanguages = (query: string) =>
  apiRequest<LanguageSearchResult[]>('/api/languages/search', { query: { q: query } });

export const addLanguage = (code: string) => apiRequest<Language>('/api/languages', { method: 'POST', body: { code } });

export const deleteLanguage = (id: number) => apiRequest<void>(`/api/languages/${id}`, { method: 'DELETE' });

export const getInterfaceTranslationOverview = () =>
  apiRequest<InterfaceTranslationOverview>('/api/interface-translations/overview');

/** Queues (or retries) filling the language's missing interface entries; the server's worker calls Azure later. */
export const queueInterfaceMachineTranslation = (languageCode: string) =>
  apiRequest<InterfaceTranslationJob>(`/api/interface-translations/${encodeURIComponent(languageCode)}/machine-translation`, { method: 'POST' });
