import { useCallback, useMemo, useState } from 'react';
import type { FieldErrors, Path, UseFormReturn } from 'react-hook-form';
import { useI18n } from '../../contexts/i18n';
import { usePatchSearchParams } from '../../hooks/usePatchSearchParams';
import { functionSubmitSchema } from './schema';
import {
  STEP_FIELDS, WIZARD_STEPS, errorCounts, firstStepWithIssues, isWizardStep, issuesByStep, reviewPathOf, stepOfField, stepOfPath,
  type FormField, type StepIssue,
} from './wizard';
import type { FunctionFormValues, WizardStep } from '../../types/adminFunctions';

/** Scroll target of a form error: the reviewable block of the field (document details share one block). */
function targetOf(field: string): string {
  const name = field.split('.')[0] as FormField;
  return name === 'documentDetails' ? 'documentDetails' : reviewPathOf(name);
}

/**
 * Step state of the service editor, kept in the `?step=` query parameter so a reload, a shared link or a correction link
 * opens the same step; plus the step-level and complete-submission validation that sends the author to the first error.
 */
export function useFunctionWizard(form: UseFormReturn<FunctionFormValues>, fallbackStep: WizardStep) {
  const { t } = useI18n();
  const [params, patch] = usePatchSearchParams();
  const requested = params.get('step');
  const step: WizardStep = isWizardStep(requested) ? requested : fallbackStep;
  // The step travels with the target: the search parameter updates a render later than component state.
  const [target, setTarget] = useState<{ path: string; step: WizardStep } | null>(null);
  const submitSchema = useMemo(() => functionSubmitSchema(t), [t]);

  const setStep = useCallback((next: WizardStep) => patch({ step: next }), [patch]);
  const clearFocus = useCallback(() => setTarget(null), []);
  const focus = useCallback((path: string, on: WizardStep) => setTarget({ path, step: on }), []);

  /** Opens the step of a field or review path and moves focus to it. */
  const goTo = useCallback((path: string) => {
    setStep(stepOfPath(path));
    focus(path, stepOfPath(path));
  }, [focus, setStep]);

  const show = useCallback((issues: StepIssue[]) => {
    issues.forEach((issue) => form.setError(issue.field as Path<FunctionFormValues>, { type: 'validate', message: issue.message }));
  }, [form]);

  /** "Next": the required and conditional fields of this step must be valid. */
  const validateStep = useCallback((current: WizardStep) => {
    form.clearErrors(STEP_FIELDS[current]);
    const issues = issuesByStep(submitSchema, form.getValues())[current] ?? [];
    if (issues.length === 0) return true;
    show(issues);
    focus(targetOf(issues[0].field), current);
    return false;
  }, [focus, form, show, submitSchema]);

  /** Final submission: every step is checked and the author is taken to the first step and field with an error. */
  const validateAll = useCallback(() => {
    form.clearErrors();
    const grouped = issuesByStep(submitSchema, form.getValues());
    const first = firstStepWithIssues(grouped);
    if (!first) return true;
    WIZARD_STEPS.forEach((key) => show(grouped[key] ?? []));
    setStep(first);
    focus(targetOf(grouped[first]?.[0]?.field ?? STEP_FIELDS[first][0]), first);
    return false;
  }, [focus, form, setStep, show, submitSchema]);

  /** Draft validation failed (e.g. no name while on a later step): open the step of the first invalid field. */
  const onInvalid = useCallback((errors: FieldErrors<FunctionFormValues>) => {
    const fields = Object.keys(errors);
    const first = WIZARD_STEPS.find((key) => fields.some((field) => stepOfField(field) === key));
    if (!first) return;
    setStep(first);
    focus(targetOf(fields.find((field) => stepOfField(field) === first) ?? STEP_FIELDS[first][0]), first);
  }, [focus, setStep]);

  return {
    step, setStep, goTo, clearFocus, validateStep, validateAll, onInvalid,
    /** Shown only once its step is on screen. */
    focusTarget: target && target.step === step ? target.path : null,
    errorCounts: errorCounts(form.formState.errors),
  };
}
