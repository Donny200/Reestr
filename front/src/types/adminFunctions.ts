import type { AssigneeSummary, CatalogFunction, DocumentDetail, DocumentForm, ServiceInstructions, TranslatedText } from './api';
import type { Translate } from '../utils/errors';

export type FunctionStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'DEACTIVATED';

export type FunctionTransition = 'submit-for-review' | 'withdraw' | 'reject' | 'publish' | 'reactivate';

/** Pending draft revision of a published service, as reported on the published record. */
export interface FunctionRevisionSummary {
  id: number;
  status: FunctionStatus;
}

export type MachineTranslationStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'SKIPPED';

/** State of the background machine translation of a service, as reported on staff responses. */
export interface MachineTranslationSummary {
  /** A provider is configured and the background worker is enabled. */
  automatic: boolean;
  status?: MachineTranslationStatus | null;
  attempts: number;
  requestedAt?: string | null;
  nextAttemptAt?: string | null;
  finishedAt?: string | null;
  /** Short, secret-free note from the last run (why it waits, what was skipped). */
  lastError?: string | null;
  /** Active languages that still lack a complete translation. */
  missingLanguages: string[];
  /** Active languages Azure Translator has no code for; they need a manual translation. */
  unsupportedLanguages?: string[];
}

export interface AdminFunction extends Omit<CatalogFunction, 'organizationId' | 'categoryId' | 'sourceLanguage'> {
  organizationId: number | null;
  categoryId: number | null;
  status: FunctionStatus;
  sourceLanguage: string;
  verifiedByUserId?: number | null;
  /** Set when this record is a draft revision: the id of the published service it will replace. */
  revisionOfId?: number | null;
  revision?: FunctionRevisionSummary | null;
  /** Responsible staff member; absent on public responses. */
  assignee?: AssigneeSummary | null;
  /** Background translation state; absent on public responses. */
  machineTranslation?: MachineTranslationSummary | null;
}

/** Content can be changed in place only for drafts and deactivated services; published ones go through a revision. */
export function isEditableStatus(status: FunctionStatus): boolean {
  return status === 'DRAFT' || status === 'DEACTIVATED';
}

export interface FunctionCategory {
  id: number;
  name: string;
  nameTranslations?: Record<string, TranslatedText>;
}

export interface FunctionOrganization {
  id: number;
  name: string;
}

export interface FunctionFormValues {
  name: string;
  description: string;
  requirements: string;
  organizationId: string;
  categoryId: string;
  sourceLanguage: string;
  whoCanUse: string;
  steps: string;
  requiredDocuments: string;
  whereHowToApply: string;
  processingTime: string;
  fee: string;
  officialSourceUrl: string;
  documentDetails: DocumentDetailValues[];
}

/** Editor values of one document detail (strings, as typed). */
export interface DocumentDetailValues {
  document: string;
  form: DocumentForm | '';
  copies: string;
  validity: string;
  notes: string;
  formUrl: string;
}

export interface CreateFunctionRequest {
  name: string;
  description: string;
  requirements: string;
  organizationId: number;
  categoryId?: number;
  sourceLanguage: string;
  instructions: ServiceInstructions;
  officialSourceUrl: string;
  documentDetails: DocumentDetail[];
}

export interface UpdateFunctionRequest extends CreateFunctionRequest {
  category?: string;
}

export interface FunctionAuditEntry {
  id: number;
  performedByUserId: number | null;
  performedBy: string;
  action: string;
  performedAt: string;
  details: string;
}

export const FUNCTION_STATUSES: FunctionStatus[] = ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'DEACTIVATED'];

const statusLabels: Record<FunctionStatus, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Pending review',
  PUBLISHED: 'Published',
  DEACTIVATED: 'Deactivated',
};

export function functionStatusLabel(status: FunctionStatus, t: Translate): string {
  return t(`fnAdmin.status.${status}`, statusLabels[status]);
}
