import type { CatalogFunction, FunctionQuery, PublicChange, UpdateFunctionRequirementsRequest } from '../types/api';
import { apiRequest, SESSIONLESS } from './http';

export function getFunctions(query: FunctionQuery = {}): Promise<CatalogFunction[]> {
  return apiRequest<CatalogFunction[]>('/api/functions', {
    query: { organizationId: query.organizationId, category: query.category }
  });
}

export function getFunction(id: number): Promise<CatalogFunction> {
  return apiRequest<CatalogFunction>(`/api/functions/${id}`);
}

/** Public history of approved publications; 404 for anything that is not published. */
export function getFunctionHistory(id: number): Promise<PublicChange[]> {
  return apiRequest<PublicChange[]>(`/api/functions/${id}/history`, SESSIONLESS);
}

export function updateFunctionRequirements(
  id: number,
  payload: UpdateFunctionRequirementsRequest
): Promise<CatalogFunction> {
  return apiRequest<CatalogFunction>(`/api/functions/${id}/requirements`, { method: 'PUT', body: payload });
}
