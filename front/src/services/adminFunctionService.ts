import { apiRequest } from './http';
import type { InstructionField, StaffMember } from '../types/api';
import type { AssignmentOverview, AssignmentState } from '../types/analytics';
import type {
  AdminFunction,
  CreateFunctionRequest,
  FunctionAuditEntry,
  FunctionCategory,
  FunctionTransition,
  MachineTranslationSummary,
  UpdateFunctionRequest,
} from '../types/adminFunctions';

export type { FunctionAuditEntry };

export const getAdminFunctions = () => apiRequest<AdminFunction[]>('/api/functions/admin');

export const getAdminFunction = (id: number) => apiRequest<AdminFunction>(`/api/functions/${id}/admin`);

export const getFunctionCategories = () => apiRequest<FunctionCategory[]>('/api/functions/categories');

export const createFunction = (body: CreateFunctionRequest) =>
  apiRequest<AdminFunction>('/api/functions', { method: 'POST', body });

export const updateFunction = (id: number, body: UpdateFunctionRequest) =>
  apiRequest<AdminFunction>(`/api/functions/${id}`, { method: 'PUT', body });

export const transitionFunction = (id: number, action: FunctionTransition, reason?: string) =>
  apiRequest<AdminFunction>(`/api/functions/${id}/${action}`, {
    method: 'POST',
    body: reason === undefined ? undefined : { reason },
  });

export const deactivateFunction = (id: number) => apiRequest<void>(`/api/functions/${id}`, { method: 'DELETE' });

/** Opens (or returns the existing) draft revision of a published service; the public version stays unchanged. */
export const createFunctionRevision = (id: number) =>
  apiRequest<AdminFunction>(`/api/functions/${id}/revisions`, { method: 'POST' });

/** Drops the draft revision of published service {id}. */
export const discardFunctionRevision = (id: number) =>
  apiRequest<void>(`/api/functions/${id}/revision`, { method: 'DELETE' });

export const getFunctionAudit = (id: number) => apiRequest<FunctionAuditEntry[]>(`/api/functions/${id}/audit`);

export interface FunctionTranslationBody {
  name: string;
  description: string;
  instructions?: Partial<Record<InstructionField, string>>;
}

export const saveFunctionTranslation = (id: number, language: string, body: FunctionTranslationBody) =>
  apiRequest<AdminFunction>(`/api/functions/${id}/translations/${encodeURIComponent(language)}`, { method: 'PUT', body });

export const verifyFunction = (id: number) => apiRequest<AdminFunction>(`/api/functions/${id}/verify`, { method: 'POST' });

/** Cards in scope with their responsible staff member; flags unassigned cards and assignees who lost access. */
export const getAssignmentOverview = (organizationId?: number, state?: AssignmentState) =>
  apiRequest<AssignmentOverview>('/api/functions/assignments', { query: { organizationId, state } });

export const getAssigneeCandidates = (id: number) => apiRequest<StaffMember[]>(`/api/functions/${id}/assignee/candidates`);

export const assignFunction = (id: number, userId: number) =>
  apiRequest<AdminFunction>(`/api/functions/${id}/assignee`, { method: 'PUT', body: { userId } });

export const unassignFunction = (id: number) => apiRequest<AdminFunction>(`/api/functions/${id}/assignee`, { method: 'DELETE' });

export const getTranslationCapabilities = () =>
  apiRequest<{ available: boolean; automatic: boolean }>('/api/functions/translation-capabilities');

/** Puts the service back on the background translation queue (after a failed or skipped run). */
export const requeueTranslation = (id: number) =>
  apiRequest<MachineTranslationSummary>(`/api/functions/${id}/translation-job`, { method: 'POST' });

export const translateFunction = (id: number, languages: string[], overwriteMachine = false) =>
  apiRequest<AdminFunction>(`/api/functions/${id}/translate`, { method: 'POST', body: { languages, overwriteMachine } });

export interface TranslationBackfillResult {
  examined: number;
  queued: number;
  alreadyQueued: number;
  limit: number;
  limitReached: boolean;
  revisionsCreated: number;
  revisionsQueued: number;
  skippedUnderReview: number;
}

/**
 * Queues services that miss translations in an active language (bounded and idempotent; the worker calls Azure later).
 * With includePublished, published services get a draft revision that is translated and then goes through review.
 */
export const backfillTranslations = (includePublished: boolean, limit = 50) =>
  apiRequest<TranslationBackfillResult>('/api/functions/translations/backfill', {
    method: 'POST',
    query: { includePublished, limit },
  });
