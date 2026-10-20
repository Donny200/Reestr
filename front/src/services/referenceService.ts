import type { Language, LanguageSearchResult, Region } from '../types/api';
import { apiRequest } from './http';

export const getRegions = () => apiRequest<Region[]>('/api/regions');

export const getLanguages = () => apiRequest<Language[]>('/api/languages');

export const getInterfaceTranslations = (languageCode: string, signal?: AbortSignal) =>
  apiRequest<Record<string, string>>(`/api/interface-translations/${encodeURIComponent(languageCode)}`, { signal });

export const searchLanguages = (query: string) =>
  apiRequest<LanguageSearchResult[]>('/api/languages/search', { query: { q: query } });

export const addLanguage = (code: string) => apiRequest<Language>('/api/languages', { method: 'POST', body: { code } });

export const deleteLanguage = (id: number) => apiRequest<void>(`/api/languages/${id}`, { method: 'DELETE' });
