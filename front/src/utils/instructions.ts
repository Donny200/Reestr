import type { CatalogFunction, InstructionField } from '../types/api';
import { requirementLines } from './format';

export interface InstructionFieldMeta {
  key: InstructionField;
  list: boolean;
  max: number;
  labelKey: string;
  label: string;
}

export const INSTRUCTION_FIELDS: InstructionFieldMeta[] = [
  { key: 'whoCanUse', list: false, max: 2000, labelKey: 'instructions.whoCanUse', label: 'Who can use this service' },
  { key: 'steps', list: true, max: 4000, labelKey: 'instructions.steps', label: 'Step-by-step procedure' },
  { key: 'requiredDocuments', list: true, max: 4000, labelKey: 'instructions.requiredDocuments', label: 'Required documents' },
  { key: 'whereHowToApply', list: false, max: 2000, labelKey: 'instructions.whereHowToApply', label: 'Where and how to apply' },
  { key: 'processingTime', list: false, max: 500, labelKey: 'instructions.processingTime', label: 'Processing time' },
  { key: 'fee', list: false, max: 500, labelKey: 'instructions.fee', label: 'Official fee' },
];

type InstructionRecord = Pick<CatalogFunction, 'instructions' | 'instructionTranslations' | 'sourceLanguage'>;

export interface LocalizedInstruction {
  text: string;
  machine: boolean;
  fallback: boolean;
}

export function instructionSource(record: InstructionRecord, field: InstructionField): string | null {
  return record.instructions?.[field]?.trim() || null;
}

export function localizedInstruction(record: InstructionRecord, field: InstructionField, locale: string): LocalizedInstruction | null {
  const source = instructionSource(record, field);
  if (!source) return null;
  const language = locale.toLowerCase();
  if (record.sourceLanguage && language === record.sourceLanguage.toLowerCase()) return { text: source, machine: false, fallback: false };
  const translation = record.instructionTranslations?.[field]?.[language];
  if (translation?.text?.trim()) return { text: translation.text, machine: translation.source === 'machine', fallback: false };
  return { text: source, machine: false, fallback: true };
}

export function instructionItems(text: string | null | undefined): string[] {
  return requirementLines(text);
}
