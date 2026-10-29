import { z } from 'zod';
import type { Translate } from '../../utils/errors';
import { optionalString, requiredString } from '../../lib/validation';
import type {
  AdminFunction, CreateFunctionRequest, DocumentDetailValues, FunctionFormValues, ServiceProfile, UpdateFunctionRequest, WizardStep,
} from '../../types/adminFunctions';
import type { DocumentDetail, DocumentForm, ServiceInstructions } from '../../types/api';
import { documentLines } from '../../utils/documentDetails';
import { INSTRUCTION_FIELDS } from '../../utils/instructions';

export const NAME_MAX = 150;
export const DESCRIPTION_MAX = 500;
export const REQUIREMENTS_MAX = 500;
export const REASON_MAX = 2000;
export const SOURCE_URL_MAX = 500;
export const MAX_LIST_ITEMS = 50;
export const VALIDITY_MAX = 200;
export const NOTES_MAX = 300;
export const COPIES_MAX = 20;
export const RISK_NOTES_MAX = 2000;

const WEB_LINK = /^https?:\/\/[^\s/?#]+\S*$/i;

export function officialLinkField(t: Translate) {
  return z
    .string()
    .trim()
    .max(SOURCE_URL_MAX)
    .refine((value) => value === '' || WEB_LINK.test(value), t('validation.url', 'Enter a full link that starts with https://'));
}

function instructionField(t: Translate, max: number, list: boolean) {
  return optionalString(max).refine(
    (value) => !list || value.split('\n').filter((line) => line.trim()).length <= MAX_LIST_ITEMS,
    t('validation.tooManyItems', 'Use at most 50 lines'),
  );
}

/**
 * Schema used when a draft is saved: formats and lengths everywhere, but only what the server needs to store a draft
 * (name and organization) is required, so a draft can be saved from any step.
 */
export function functionFormSchema(t: Translate) {
  const instructions = Object.fromEntries(
    INSTRUCTION_FIELDS.map((field) => [field.key, instructionField(t, field.max, field.list)]),
  ) as Record<keyof ServiceInstructions, ReturnType<typeof instructionField>>;
  return z.object({
    name: requiredString(t, NAME_MAX),
    description: optionalString(DESCRIPTION_MAX),
    requirements: optionalString(REQUIREMENTS_MAX),
    organizationId: z.string().min(1, t('validation.selectOrg')),
    categoryId: z.string(),
    sourceLanguage: z.string().min(1, t('validation.required')),
    ...instructions,
    officialSourceUrl: officialLinkField(t),
    documentDetails: z.array(z.object({
      document: z.string(),
      form: z.string(),
      copies: z.string().trim().refine((value) => value === '' || (/^\d+$/.test(value) && Number(value) >= 1 && Number(value) <= COPIES_MAX),
        t('documents.copiesInvalid', 'Enter a number from 1 to 20, or leave it empty')),
      validity: z.string().max(VALIDITY_MAX),
      notes: z.string().max(NOTES_MAX),
      formUrl: officialLinkField(t),
    })),
    deliveryChannel: z.string(),
    onlineServiceUrl: officialLinkField(t),
    provisionLevel: z.string(),
    riskLevel: z.string(),
    riskNotes: optionalString(RISK_NOTES_MAX),
  });
}

/**
 * Schema of a complete submission (Next on a step and "Submit for review"): the draft rules plus every required field
 * and the conditional rules of the digitalization and risk steps. The server checks the conditional rules as well.
 */
export function functionSubmitSchema(t: Translate) {
  return functionFormSchema(t).superRefine((values, context) => {
    if (!values.description.trim()) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['description'], message: t('validation.required') });
    }
    if ((values.deliveryChannel === 'ONLINE' || values.deliveryChannel === 'ONLINE_AND_IN_PERSON') && !values.onlineServiceUrl.trim()) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['onlineServiceUrl'], message: t('validation.onlineServiceUrlRequired') });
    }
    if (values.riskLevel === 'HIGH' && !values.riskNotes.trim()) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['riskNotes'], message: t('validation.riskNotesRequired') });
    }
  });
}

export function emptyFunctionForm(sourceLanguage: string): FunctionFormValues {
  return {
    name: '',
    description: '',
    requirements: '',
    organizationId: '',
    categoryId: '',
    sourceLanguage,
    whoCanUse: '',
    steps: '',
    requiredDocuments: '',
    whereHowToApply: '',
    processingTime: '',
    fee: '',
    officialSourceUrl: '',
    documentDetails: [],
    deliveryChannel: '',
    onlineServiceUrl: '',
    provisionLevel: '',
    riskLevel: '',
    riskNotes: '',
  };
}

export function formOf(record: AdminFunction): FunctionFormValues {
  return {
    name: record.name,
    description: record.description ?? '',
    requirements: record.requirements ?? '',
    organizationId: record.organizationId === null ? '' : String(record.organizationId),
    categoryId: record.categoryId === null ? '' : String(record.categoryId),
    sourceLanguage: record.sourceLanguage,
    whoCanUse: record.instructions?.whoCanUse ?? '',
    steps: record.instructions?.steps ?? '',
    requiredDocuments: record.instructions?.requiredDocuments ?? '',
    whereHowToApply: record.instructions?.whereHowToApply ?? '',
    processingTime: record.instructions?.processingTime ?? '',
    fee: record.instructions?.fee ?? '',
    officialSourceUrl: record.officialSourceUrl ?? '',
    documentDetails: (record.documentDetails ?? []).map((detail) => ({
      ...(detail.id ? { id: detail.id } : {}),
      document: detail.document,
      form: (detail.form ?? '') as DocumentForm | '',
      copies: detail.copies == null ? '' : String(detail.copies),
      validity: detail.validity ?? '',
      notes: detail.notes ?? '',
      formUrl: detail.formUrl ?? '',
    })),
    deliveryChannel: record.profile?.deliveryChannel ?? '',
    onlineServiceUrl: record.profile?.onlineServiceUrl ?? '',
    provisionLevel: record.profile?.provisionLevel ?? '',
    riskLevel: record.profile?.riskLevel ?? '',
    riskNotes: record.profile?.riskNotes ?? '',
  };
}

/** Details of document lines that still exist, without empty ones, as the API expects them. */
export function documentDetailsOf(values: Pick<FunctionFormValues, 'requiredDocuments' | 'documentDetails'>): DocumentDetail[] {
  const lines = documentLines(values.requiredDocuments);
  return values.documentDetails
    .filter((detail) => lines.includes(detail.document.trim()))
    .map((detail: DocumentDetailValues) => ({
      ...(detail.id ? { id: detail.id } : {}),
      document: detail.document.trim(),
      form: detail.form || null,
      copies: detail.copies.trim() ? Number(detail.copies) : null,
      validity: detail.validity.trim() || null,
      notes: detail.notes.trim() || null,
      formUrl: detail.formUrl.trim() || null,
    }))
    .filter((detail) => detail.form || detail.copies || detail.validity || detail.notes || detail.formUrl);
}

function instructionsOf(values: FunctionFormValues): ServiceInstructions {
  const text = (value: string) => value.trim() || null;
  return {
    whoCanUse: text(values.whoCanUse),
    steps: text(values.steps),
    requiredDocuments: text(values.requiredDocuments),
    whereHowToApply: text(values.whereHowToApply),
    processingTime: text(values.processingTime),
    fee: text(values.fee),
  };
}

function profileOf(values: FunctionFormValues): ServiceProfile {
  return {
    deliveryChannel: values.deliveryChannel || null,
    onlineServiceUrl: values.onlineServiceUrl.trim() || null,
    provisionLevel: values.provisionLevel || null,
    riskLevel: values.riskLevel || null,
    riskNotes: values.riskNotes.trim() || null,
  };
}

export function toCreateRequest(values: FunctionFormValues, editorStep?: WizardStep): CreateFunctionRequest {
  return {
    name: values.name.trim(),
    description: values.description.trim(),
    requirements: values.requirements,
    organizationId: Number(values.organizationId),
    categoryId: values.categoryId ? Number(values.categoryId) : undefined,
    sourceLanguage: values.sourceLanguage,
    instructions: instructionsOf(values),
    officialSourceUrl: values.officialSourceUrl.trim(),
    documentDetails: documentDetailsOf(values),
    profile: profileOf(values),
    editorStep,
  };
}

export function toUpdateRequest(values: FunctionFormValues, editorStep?: WizardStep): UpdateFunctionRequest {
  return { ...toCreateRequest(values, editorStep), category: values.categoryId ? undefined : '' };
}
