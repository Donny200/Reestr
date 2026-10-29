import type { AssigneeSummary, CatalogFunction, DocumentDetail, DocumentForm, ServiceInstructions, TranslatedText } from './api';
import type { Translate } from '../utils/errors';

/** CHANGES_REQUESTED: a reviewer returned the submission with notes on specific fields; editable, not public. */
export type FunctionStatus = 'DRAFT' | 'PENDING_REVIEW' | 'CHANGES_REQUESTED' | 'PUBLISHED' | 'DEACTIVATED';

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
  /** Staff-only digitalization, decentralization and risk profile; absent on public responses. */
  profile?: ServiceProfile | null;
  /** Editor step the author last saved from. */
  editorStep?: WizardStep | null;
  /** Review cycle (number of submissions) and open field-level corrections. */
  review?: { cycle: number; openCorrections?: number | null } | null;
}

/**
 * Content can be changed in place only for drafts, services returned with corrections and deactivated services;
 * published ones go through a revision.
 */
export function isEditableStatus(status: FunctionStatus): boolean {
  return status === 'DRAFT' || status === 'DEACTIVATED' || status === 'CHANGES_REQUESTED';
}

/** Steps of the service editor, in order; reviewers inspect a submission in the same sections. */
export type WizardStep = 'general' | 'mechanism' | 'digitalization' | 'decentralization' | 'other';

export type DeliveryChannel = 'IN_PERSON' | 'ONLINE' | 'ONLINE_AND_IN_PERSON';
export type ProvisionLevel = 'NATIONAL' | 'REGIONAL' | 'DISTRICT';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export const DELIVERY_CHANNELS: DeliveryChannel[] = ['IN_PERSON', 'ONLINE', 'ONLINE_AND_IN_PERSON'];
export const PROVISION_LEVELS: ProvisionLevel[] = ['NATIONAL', 'REGIONAL', 'DISTRICT'];
export const RISK_LEVELS: RiskLevel[] = ['LOW', 'MEDIUM', 'HIGH'];

export interface ServiceProfile {
  deliveryChannel?: DeliveryChannel | null;
  onlineServiceUrl?: string | null;
  provisionLevel?: ProvisionLevel | null;
  riskLevel?: RiskLevel | null;
  riskNotes?: string | null;
}

export type ReviewResolution = 'ADDRESSED' | 'REVIEWER_RESOLVED' | 'REVISION_DISCARDED';

/** A reviewer's correction request on one field (`instructions.fee`, `documentDetails.<id>`) or a whole step (`section.other`). */
export interface ReviewComment {
  id: number;
  functionId: number;
  fieldPath: string;
  section: WizardStep;
  reviewCycle: number;
  comment: string;
  status: 'OPEN' | 'RESOLVED';
  reviewerUserId?: number | null;
  reviewer: string;
  createdAt: string;
  /** Open note whose field has changed since the review (it closes when the service is resubmitted). */
  addressed: boolean;
  resolution?: ReviewResolution | null;
  resolvedBy?: string | null;
  resolvedAt?: string | null;
  resolvedInCycle?: number | null;
}

export interface ReviewMark {
  section: WizardStep;
  reviewer: string;
  reviewedAt: string;
}

export interface FunctionReview {
  functionId: number;
  status: FunctionStatus;
  reviewCycle: number;
  comments: ReviewComment[];
  reviewedSections: ReviewMark[];
  /** Open notes whose field is unchanged; resubmission waits for these. */
  unaddressedFields: string[];
  canResubmit: boolean;
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
  deliveryChannel: DeliveryChannel | '';
  onlineServiceUrl: string;
  provisionLevel: ProvisionLevel | '';
  riskLevel: RiskLevel | '';
  riskNotes: string;
}

/** Editor values of one document detail (strings, as typed). */
export interface DocumentDetailValues {
  /** Stable id kept across edits; absent until the detail is first filled in. */
  id?: string;
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
  profile?: ServiceProfile;
  editorStep?: WizardStep;
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

export const FUNCTION_STATUSES: FunctionStatus[] = ['DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'PUBLISHED', 'DEACTIVATED'];

const statusLabels: Record<FunctionStatus, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Pending review',
  CHANGES_REQUESTED: 'Changes requested',
  PUBLISHED: 'Published',
  DEACTIVATED: 'Deactivated',
};

export function functionStatusLabel(status: FunctionStatus, t: Translate): string {
  return t(`fnAdmin.status.${status}`, statusLabels[status]);
}
