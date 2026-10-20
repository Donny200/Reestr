import { localizedText } from './translations';
import { INSTRUCTION_FIELDS, instructionSource } from './instructions';
import type { AdminFunction } from '../types/adminFunctions';
import type { InstructionField } from '../types/api';

export function functionText(record: AdminFunction, field: 'name' | 'description', language: string): string {
  if (language === record.sourceLanguage) return record[field] ?? '';
  const translations = field === 'name' ? record.nameTranslations : record.descriptionTranslations;
  return localizedText(record[field], translations, language) ?? '';
}

export function translatableInstructions(record: AdminFunction): InstructionField[] {
  return INSTRUCTION_FIELDS.map((field) => field.key).filter((key) => instructionSource(record, key) !== null);
}

export function hasFunctionTranslation(record: AdminFunction, languageCode: string): boolean {
  const language = languageCode.toLowerCase();
  if (language === record.sourceLanguage.toLowerCase()) return true;
  const name = record.nameTranslations?.[language]?.text?.trim();
  const description = record.descriptionTranslations?.[language]?.text?.trim();
  const instructions = translatableInstructions(record).every((key) => record.instructionTranslations?.[key]?.[language]?.text?.trim());
  return Boolean(name && (!record.description || description) && instructions);
}
