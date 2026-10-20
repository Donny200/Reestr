import type { PublicChange } from '../../types/api';
import type { Translate } from '../../utils/errors';

export const CHANGE_SECTION_LABELS: Record<string, { key: string; fallback: string }> = {
  name: { key: 'field.name', fallback: 'Name' },
  description: { key: 'field.description', fallback: 'Description' },
  documents: { key: 'instructions.requiredDocuments', fallback: 'Required documents' },
  steps: { key: 'instructions.steps', fallback: 'Step-by-step procedure' },
  whoCanUse: { key: 'instructions.whoCanUse', fallback: 'Who can use this service' },
  whereHowToApply: { key: 'instructions.whereHowToApply', fallback: 'Where and how to apply' },
  processingTime: { key: 'instructions.processingTime', fallback: 'Processing time' },
  fee: { key: 'instructions.fee', fallback: 'Official fee' },
  sourceLink: { key: 'verification.officialSource', fallback: 'Source link' },
  organization: { key: 'field.organization', fallback: 'Organization' },
  category: { key: 'field.category', fallback: 'Category' },
  translations: { key: 'history.translations', fallback: 'Translations' },
};

export function sectionLabel(section: string, t: Translate): string {
  const entry = CHANGE_SECTION_LABELS[section];
  return entry ? t(entry.key, entry.fallback) : section;
}

export function changeTypeLabel(change: PublicChange, t: Translate): string {
  switch (change.changeType) {
    case 'FIRST_PUBLICATION':
      return t('history.firstPublication', 'First published');
    case 'REPUBLISHED':
      return t('history.republished', 'Published again');
    default:
      return change.sections.length > 0 ? t('history.updated', 'Updated') : t('history.updatedGeneric', 'Information updated');
  }
}

