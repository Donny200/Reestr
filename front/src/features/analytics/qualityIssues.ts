import type { QualityIssue, QualityIssueReason, QualityIssueType } from '../../types/analytics';
import type { Translate } from '../../utils/errors';

export const qualityIssueLabels: Record<QualityIssueType, string> = {
  VERIFICATION_OVERDUE: 'Verification overdue',
  SOURCE_MISSING: 'External source link unusable',
  INFORMATION_INCOMPLETE: 'Required information incomplete',
  TRANSLATIONS_MISSING: 'Translations missing',
};

export const qualityIssueHints: Record<QualityIssueType, string> = {
  VERIFICATION_OVERDUE: 'Published cards not reviewed within 180 days',
  SOURCE_MISSING: 'The optional external link is not a valid web address',
  INFORMATION_INCOMPLETE: 'Fields the editor marks as required are empty',
  TRANSLATIONS_MISSING: 'Not translated into every active language',
};

const reasonText: Record<QualityIssueReason, string> = {
  NEVER_VERIFIED: 'Published but never marked as verified by the organization. Review the card and mark it as verified.',
  RECHECK_DUE: 'The last review is more than 180 days old. Review the card again.',
  CHANGED_SINCE_VERIFICATION: 'The card changed after its last review. Review it again.',
  UNUSABLE: 'The external source link is not a valid web address. Replace it with a full https:// link or remove it.',
  REQUIRED_FIELDS: 'Fill in the required fields:',
  LANGUAGES: 'Add translations for:',
};

const fieldLabels: Record<string, { key: string; fallback: string }> = {
  name: { key: 'field.name', fallback: 'Name' },
  description: { key: 'field.description', fallback: 'Description' },
  organization: { key: 'field.organization', fallback: 'Organization' },
};

export const issueAnchors: Record<QualityIssueType, string> = {
  VERIFICATION_OVERDUE: 'function-verification',
  SOURCE_MISSING: 'function-details',
  INFORMATION_INCOMPLETE: 'function-details',
  TRANSLATIONS_MISSING: 'function-translations',
};

export function issueLabel(type: QualityIssueType, t: Translate): string {
  return t(`quality.issue.${type}`, qualityIssueLabels[type]);
}

export function issueGuidance(issue: QualityIssue, t: Translate, languageName: (code: string) => string): string {
  const base = t(`quality.reason.${issue.reason}`, reasonText[issue.reason]);
  if (issue.reason === 'REQUIRED_FIELDS') {
    return `${base} ${issue.details.map((field) => (fieldLabels[field] ? t(fieldLabels[field].key, fieldLabels[field].fallback) : field)).join(', ')}`;
  }
  if (issue.reason === 'LANGUAGES') return `${base} ${issue.details.map(languageName).join(', ')}`;
  if (issue.reason === 'UNUSABLE' && issue.details[0]) return `${base} (${issue.details[0]})`;
  return base;
}

export function editorPath(functionId: number, type: QualityIssueType): string {
  return `/admin/functions/${functionId}#${issueAnchors[type]}`;
}
