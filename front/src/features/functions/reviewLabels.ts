import { useI18n } from '../../contexts/i18n';
import { INSTRUCTION_FIELDS } from '../../utils/instructions';
import { DOCUMENT_PREFIX, SECTION_PREFIX, fieldOfPath, isWizardStep, type FormField } from './wizard';
import type { DocumentDetailValues, FunctionReview, WizardStep } from '../../types/adminFunctions';

/** Label of a form field, also used to name it in review notes and the list of corrections. */
export function useFieldLabel() {
  const { t } = useI18n();
  return (field: FormField): string => {
    const instruction = INSTRUCTION_FIELDS.find((item) => item.key === field);
    if (instruction) return t(instruction.labelKey);
    switch (field) {
      case 'name': return t('field.name');
      case 'description': return t('field.description');
      case 'organizationId': return t('field.organization');
      case 'categoryId': return t('field.category');
      case 'sourceLanguage': return t('fnAdmin.sourceLanguage');
      case 'requirements': return t('field.requirements');
      case 'officialSourceUrl': return t('verification.officialSourceUrl');
      case 'documentDetails': return t('documents.editorTitle');
      case 'deliveryChannel': return t('profile.deliveryChannel');
      case 'onlineServiceUrl': return t('profile.onlineServiceUrl');
      case 'provisionLevel': return t('profile.provisionLevel');
      case 'riskLevel': return t('profile.riskLevel');
      case 'riskNotes': return t('profile.riskNotes');
      default: return field;
    }
  };
}

/** Human name of a review path: a field label, "Whole section: …", "Document: …" or "Removed document". */
export function useReviewPathLabel(documentDetails: DocumentDetailValues[]) {
  const { t } = useI18n();
  const label = useFieldLabel();
  return (path: string): string => {
    if (path.startsWith(SECTION_PREFIX)) {
      const step = path.slice(SECTION_PREFIX.length);
      return t('review.sectionNote', { name: isWizardStep(step) ? t(`wizard.step.${step}`) : step });
    }
    if (path.startsWith(DOCUMENT_PREFIX)) {
      const id = path.slice(DOCUMENT_PREFIX.length);
      const detail = documentDetails.find((item) => item.id === id);
      return detail ? t('review.documentItem', { name: detail.document }) : t('review.removedItem');
    }
    const field = fieldOfPath(path);
    return field ? label(field) : path;
  };
}

/** Steps that have open corrections, with their number. */
export function correctionCountsOf(review: FunctionReview | undefined): Partial<Record<WizardStep, number>> {
  const counts: Partial<Record<WizardStep, number>> = {};
  for (const comment of review?.comments ?? []) {
    if (comment.status === 'OPEN') counts[comment.section] = (counts[comment.section] ?? 0) + 1;
  }
  return counts;
}
