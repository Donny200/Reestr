import { useId, type ReactNode } from 'react';
import { AlertCircleIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';
import { FieldControlContext, type FieldControlContextValue } from './fieldContext';

export interface FieldControl {
  id: string;
  invalid: boolean;
  valid: boolean;
  describedBy: string | undefined;
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  success?: string;
  required?: boolean;
  className?: string;
  children: (control: FieldControl) => ReactNode;
}

export const fieldLabelClass = 'block text-xs font-medium uppercase tracking-[0.025em] text-secondary';

export function FieldMessage({ id, error, success, hint }: { id?: string; error?: string; success?: string; hint?: string }) {
  if (error) {
    return (
      <p id={id} role="alert" className="flex items-start gap-1.5 text-xs font-medium leading-5 text-danger">
        <AlertCircleIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>{error}</span>
      </p>
    );
  }
  if (success) return <p id={id} role="status" className="text-xs font-medium leading-5 text-status-published">{success}</p>;
  if (hint) return <p id={id} className="text-xs leading-5 text-secondary">{hint}</p>;
  return null;
}

export function Field({ label, error, hint, success, required = false, className, children }: FieldProps) {
  const { t } = useI18n();
  const id = useId();
  const messageId = error || success || hint ? `${id}-message` : undefined;
  const invalid = Boolean(error);
  const valid = Boolean(success && !error);
  const context: FieldControlContextValue = {
    id, invalid, valid, describedBy: messageId, errorId: error ? messageId : undefined, required,
  };

  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={id} className={fieldLabelClass}>
        {label}
        {required && (
          <>
            <span className="ms-0.5 text-danger" aria-hidden="true">*</span>
            <span className="sr-only"> ({t('validation.required')})</span>
          </>
        )}
      </label>
      <FieldControlContext.Provider value={context}>{children({ id, invalid, valid, describedBy: messageId })}</FieldControlContext.Provider>
      <FieldMessage id={messageId} error={error} success={success} hint={hint} />
    </div>
  );
}
