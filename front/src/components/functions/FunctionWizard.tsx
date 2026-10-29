import { useEffect, useRef, type ReactNode } from 'react';
import type { UseFormReturn } from 'react-hook-form';
import { AlertCircleIcon, CheckIcon, ChevronLeftIcon, ChevronRightIcon, MessageSquareWarningIcon } from 'lucide-react';
import { Button } from '../ui/Button';
import { ActionBar } from '../ui/ActionBar';
import { FunctionFields } from './FunctionFields';
import { Reviewable } from './ReviewFeedback';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import { WIZARD_STEPS, anchorOf } from '../../features/functions/wizard';
import type { FunctionCategory, FunctionFormValues, FunctionOrganization, WizardStep } from '../../types/adminFunctions';

interface Props {
  form: UseFormReturn<FunctionFormValues>;
  organizations: FunctionOrganization[];
  categories: FunctionCategory[];
  step: WizardStep;
  onStepChange: (step: WizardStep) => void;
  disabled: boolean;
  /** Checks the current step before "Next"; returns false to stay (errors are then shown on the step). */
  validateStep?: (step: WizardStep) => boolean;
  errorCounts?: Partial<Record<WizardStep, number>>;
  correctionCounts?: Partial<Record<WizardStep, number>>;
  reviewedSteps?: ReadonlySet<WizardStep>;
  /** Extra controls in a step header (e.g. the reviewer's "Mark section as reviewed"). */
  stepActions?: (step: WizardStep) => ReactNode;
  /** Review path (see reviewPathOf) to scroll to and focus once the step is shown. */
  focusTarget?: string | null;
  onFocusHandled?: () => void;
  /** Save / submit buttons shown next to the step navigation. */
  actions?: ReactNode;
  status?: ReactNode;
  children?: ReactNode;
}

const CONTROLS = 'input:not([type=hidden]):not(:disabled), textarea:not(:disabled), button[role=combobox]:not(:disabled)';

/** The invalid control inside a field block, else its first control, else the block itself. */
function focusable(container: Element): HTMLElement | null {
  return container.querySelector<HTMLElement>('[aria-invalid="true"]')
    ?? container.querySelector<HTMLElement>(CONTROLS)
    ?? (container instanceof HTMLElement ? container : null);
}

/**
 * The service editor as a sequence of steps: a stepper with the progress and the state of each step (errors, open
 * corrections, reviewed), the fields of the current step, and Previous / Next navigation. All steps share one form, so
 * moving between steps never loses what was typed.
 */
export function FunctionWizard({
  form, organizations, categories, step, onStepChange, disabled, validateStep, errorCounts = {}, correctionCounts = {},
  reviewedSteps, stepActions, focusTarget, onFocusHandled, actions, status, children,
}: Props) {
  const { t } = useI18n();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const navigated = useRef(false);
  const index = WIZARD_STEPS.indexOf(step);
  const total = WIZARD_STEPS.length;
  const name = t(`wizard.step.${step}`);

  useEffect(() => {
    if (focusTarget) {
      const frame = window.requestAnimationFrame(() => {
        const target = document.getElementById(anchorOf(focusTarget));
        if (target) {
          if (!target.hasAttribute('tabindex') && !target.querySelector(CONTROLS)) target.setAttribute('tabindex', '-1');
          target.scrollIntoView({ block: 'center', behavior: 'smooth' });
          focusable(target)?.focus({ preventScroll: true });
        }
        onFocusHandled?.();
      });
      return () => window.cancelAnimationFrame(frame);
    }
    if (navigated.current) {
      navigated.current = false;
      headingRef.current?.focus();
    }
    return undefined;
  }, [step, focusTarget, onFocusHandled]);

  const go = (next: WizardStep) => {
    navigated.current = true;
    onStepChange(next);
  };

  const next = () => {
    if (index >= total - 1) return;
    if (validateStep && !validateStep(step)) return;
    go(WIZARD_STEPS[index + 1]);
  };

  return (
    <div>
      <nav aria-label={t('wizard.progressLabel')} className="border-b border-line px-5 py-4">
        <div className="mb-3 flex items-center justify-between gap-3 text-sm">
          <p className="font-medium text-foreground">{t('wizard.progress', { current: index + 1, total })}</p>
          <p className="truncate text-secondary sm:hidden">{name}</p>
        </div>
        <div
          role="progressbar"
          aria-label={t('wizard.progressLabel')}
          aria-valuemin={1}
          aria-valuemax={total}
          aria-valuenow={index + 1}
          aria-valuetext={t('wizard.stepAnnouncement', { current: index + 1, total, name })}
          className="mb-4 h-1.5 overflow-hidden rounded-pill bg-surface-2"
        >
          <div className="h-full rounded-pill bg-ink transition-[width] duration-snap ease-snap" style={{ width: `${((index + 1) / total) * 100}%` }} />
        </div>
        <ol className="flex justify-between gap-1 sm:grid sm:grid-cols-5 sm:gap-2">
          {WIZARD_STEPS.map((key, position) => {
            const current = key === step;
            const errors = errorCounts[key] ?? 0;
            const corrections = correctionCounts[key] ?? 0;
            const reviewed = reviewedSteps?.has(key) ?? false;
            return (
              <li key={key} className="min-w-0">
                <button
                  type="button"
                  aria-current={current ? 'step' : undefined}
                  onClick={() => (current ? undefined : go(key))}
                  className={cn(
                    'flex w-full min-w-0 flex-col items-center gap-1.5 rounded-control px-1 py-2 text-center text-xs leading-4 transition-colors sm:items-start sm:px-2 sm:text-start',
                    'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink',
                    current ? 'bg-surface text-foreground' : 'text-secondary fine:hover:bg-surface fine:hover:text-foreground',
                  )}
                >
                  <span className="flex items-center gap-1.5">
                    <span
                      className={cn(
                        'flex h-7 w-7 shrink-0 items-center justify-center rounded-pill border text-xs font-semibold tabular-nums',
                        current ? 'border-ink bg-ink text-ink-fg' : reviewed ? 'border-status-published bg-status-published-bg text-status-published' : 'border-line',
                      )}
                      aria-hidden="true"
                    >
                      {reviewed && !current ? <CheckIcon className="h-3.5 w-3.5" /> : position + 1}
                    </span>
                    {errors > 0 && <AlertCircleIcon className="h-3.5 w-3.5 text-danger" aria-hidden="true" />}
                    {corrections > 0 && <MessageSquareWarningIcon className="h-3.5 w-3.5 text-status-pending" aria-hidden="true" />}
                  </span>
                  <span className="sr-only sm:not-sr-only sm:line-clamp-2 sm:font-medium">{t(`wizard.step.${key}`)}</span>
                  {errors > 0 && <span className="sr-only">{t('wizard.stepErrors', { count: errors })}</span>}
                  {corrections > 0 && <span className="sr-only">{t('review.openCorrections', { count: corrections })}</span>}
                  {reviewed && <span className="sr-only">{t('review.reviewed')}</span>}
                </button>
              </li>
            );
          })}
        </ol>
      </nav>
      <p className="sr-only" aria-live="polite">{t('wizard.stepAnnouncement', { current: index + 1, total, name })}</p>

      <div className="space-y-5 px-5 py-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h2 ref={headingRef} tabIndex={-1} className="text-lg font-semibold text-foreground outline-none">{name}</h2>
            <p className="mt-1 text-sm leading-6 text-secondary">{t(`wizard.stepHint.${step}`)}</p>
          </div>
          {stepActions?.(step)}
        </div>
        <Reviewable path={`section.${step}`} label={name}>
          <FunctionFields form={form} step={step} organizations={organizations} categories={categories} disabled={disabled} />
        </Reviewable>
        {children}
      </div>

      <ActionBar status={status}>
        {index > 0 && (
          <Button variant="outline" icon={<ChevronLeftIcon />} onClick={() => go(WIZARD_STEPS[index - 1])}>
            {t('wizard.previous')}
          </Button>
        )}
        {index < total - 1 && (
          <Button variant="outline" onClick={next}>
            {t('wizard.next')}
            <ChevronRightIcon className="rtl:-scale-x-100" aria-hidden="true" />
          </Button>
        )}
        {actions}
      </ActionBar>
    </div>
  );
}
