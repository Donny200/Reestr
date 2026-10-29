import type { FieldErrors } from 'react-hook-form';
import type { z } from 'zod';
import type { FunctionFormValues, ReviewComment, WizardStep } from '../../types/adminFunctions';

/** Editor steps in order. The reviewer inspects a submission in the same sections, and review notes use the same paths. */
export const WIZARD_STEPS: WizardStep[] = ['general', 'mechanism', 'digitalization', 'decentralization', 'other'];

export type FormField = keyof FunctionFormValues;

/** Form fields of each step, in the order they are shown. */
export const STEP_FIELDS: Record<WizardStep, FormField[]> = {
  general: ['name', 'description', 'organizationId', 'categoryId', 'sourceLanguage'],
  mechanism: ['whoCanUse', 'steps', 'requiredDocuments', 'documentDetails', 'processingTime', 'fee'],
  digitalization: ['deliveryChannel', 'onlineServiceUrl', 'whereHowToApply'],
  decentralization: ['provisionLevel', 'riskLevel', 'riskNotes'],
  other: ['requirements', 'officialSourceUrl'],
};

const INSTRUCTION_KEYS = new Set<FormField>(['whoCanUse', 'steps', 'requiredDocuments', 'whereHowToApply', 'processingTime', 'fee']);
const PROFILE_KEYS = new Set<FormField>(['deliveryChannel', 'onlineServiceUrl', 'provisionLevel', 'riskLevel', 'riskNotes']);

export const SECTION_PREFIX = 'section.';
export const DOCUMENT_PREFIX = 'documentDetails.';

export function isWizardStep(value: string | null | undefined): value is WizardStep {
  return WIZARD_STEPS.includes(value as WizardStep);
}

/** Stable review path of a form field, as the server stores review notes: `instructions.fee`, `profile.riskLevel`, `name`. */
export function reviewPathOf(field: FormField): string {
  if (INSTRUCTION_KEYS.has(field)) return `instructions.${field}`;
  if (PROFILE_KEYS.has(field)) return `profile.${field}`;
  return field;
}

/** The form field a review path points at; null for sections and document items. */
export function fieldOfPath(path: string): FormField | null {
  const name = path.replace(/^(instructions|profile)\./, '') as FormField;
  return Object.values(STEP_FIELDS).some((fields) => fields.includes(name)) && !path.startsWith(DOCUMENT_PREFIX) ? name : null;
}

export function stepOfField(field: string): WizardStep {
  const name = field.split('.')[0] as FormField;
  return WIZARD_STEPS.find((step) => STEP_FIELDS[step].includes(name)) ?? 'general';
}

export function stepOfPath(path: string): WizardStep {
  if (path.startsWith(SECTION_PREFIX)) {
    const step = path.slice(SECTION_PREFIX.length);
    return isWizardStep(step) ? step : 'general';
  }
  if (path.startsWith(DOCUMENT_PREFIX)) return 'mechanism';
  return stepOfField(fieldOfPath(path) ?? path);
}

/** DOM id of the block wrapping a reviewable field, used to scroll to it from the list of corrections. */
export function anchorOf(path: string): string {
  return `review-${path.replace(/[^A-Za-z0-9_-]/g, '-')}`;
}

/** Number of current form errors per step. */
export function errorCounts(errors: FieldErrors<FunctionFormValues>): Partial<Record<WizardStep, number>> {
  const counts: Partial<Record<WizardStep, number>> = {};
  for (const [field, error] of Object.entries(errors)) {
    if (!error) continue;
    const step = stepOfField(field);
    const nested = field === 'documentDetails' && Array.isArray(error) ? error.filter(Boolean).length : 1;
    counts[step] = (counts[step] ?? 0) + nested;
  }
  return counts;
}

export interface StepIssue {
  /** react-hook-form path, e.g. `onlineServiceUrl` or `documentDetails.2.copies`. */
  field: string;
  message: string;
}

/** Validation issues of a complete submission, grouped by the step they belong to. */
export function issuesByStep(schema: z.ZodTypeAny, values: FunctionFormValues): Partial<Record<WizardStep, StepIssue[]>> {
  const result = schema.safeParse(values);
  const grouped: Partial<Record<WizardStep, StepIssue[]>> = {};
  if (result.success) return grouped;
  for (const issue of result.error.issues) {
    const field = issue.path.join('.');
    const step = stepOfField(field);
    const list = grouped[step] ?? [];
    if (!list.some((item) => item.field === field)) list.push({ field, message: issue.message });
    grouped[step] = list;
  }
  return grouped;
}

/** First step (in wizard order) that has issues. */
export function firstStepWithIssues(issues: Partial<Record<WizardStep, unknown[]>>): WizardStep | null {
  return WIZARD_STEPS.find((step) => (issues[step]?.length ?? 0) > 0) ?? null;
}

/** Open notes keyed by path; a newer note replaces an older one on the same path. */
export function openCommentsByPath(comments: ReviewComment[] | undefined): Map<string, ReviewComment> {
  const map = new Map<string, ReviewComment>();
  for (const comment of comments ?? []) if (comment.status === 'OPEN') map.set(comment.fieldPath, comment);
  return map;
}
