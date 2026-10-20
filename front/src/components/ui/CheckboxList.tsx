import { useId } from 'react';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';
import { fieldLabelClass, FieldMessage } from './Field';
import { Checkbox } from './Input';

export interface CheckboxOption {
  value: number;
  label: string;
  description?: string;
  disabled?: boolean;
}

interface CheckboxListProps {
  legend: string;
  options: CheckboxOption[];
  selected: number[];
  onChange: (values: number[]) => void;
  error?: string;
  hint?: string;
  className?: string;
  columns?: 1 | 2;
  required?: boolean;
}

export function CheckboxList({ legend, options, selected, onChange, error, hint, className, columns = 1, required = true }: CheckboxListProps) {
  const { t } = useI18n();
  const messageId = useId();
  const toggle = (value: number) =>
    onChange(selected.includes(value) ? selected.filter((item) => item !== value) : [...selected, value]);

  return (
    <fieldset className={cn('space-y-1.5', className)} aria-invalid={Boolean(error) || undefined} aria-describedby={error || hint ? messageId : undefined}>
      <legend className={fieldLabelClass}>
        {legend}
        {required && (
          <>
            <span className="ms-0.5 text-danger" aria-hidden="true">*</span>
            <span className="sr-only"> ({t('validation.required')})</span>
          </>
        )}
      </legend>
      <div className={cn('max-h-64 gap-1 overflow-y-auto rounded-control border bg-surface-field p-1.5', columns === 2 ? 'grid sm:grid-cols-2' : 'grid', error ? 'border-danger' : 'border-line')}>
        {options.map((option) => {
          const descriptionId = option.description ? `${messageId}-${option.value}` : undefined;
          return (
            <label
              key={option.value}
              className={cn(
                'flex min-h-10 cursor-pointer items-start gap-3 rounded-control px-2.5 py-2 text-sm text-foreground transition-colors duration-snap fine:hover:bg-surface',
                option.disabled && 'cursor-not-allowed text-secondary fine:hover:bg-transparent',
              )}
            >
              <Checkbox
                className="mt-0.5"
                checked={selected.includes(option.value)}
                disabled={option.disabled}
                aria-describedby={descriptionId}
                onChange={() => toggle(option.value)}
              />
              <span className="min-w-0">
                <span className="block font-medium leading-snug wrap-anywhere">{option.label}</span>
                {option.description && <span id={descriptionId} className="mt-0.5 block text-xs text-secondary">{option.description}</span>}
              </span>
            </label>
          );
        })}
      </div>
      <FieldMessage id={messageId} error={error} hint={hint} />
    </fieldset>
  );
}
