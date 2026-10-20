import { forwardRef, useContext, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { CheckIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { FieldControlContext, type FieldControlContextValue } from './fieldContext';

export const controlClass =
  'w-full min-w-0 rounded-control border border-line bg-surface-field px-4 py-3 text-base text-foreground transition-[border-color,background-color] duration-snap ease-snap placeholder:text-secondary focus:border-line-strong focus:bg-background disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-secondary';

interface ControlStateProps {
  invalid?: boolean;
  valid?: boolean;
  describedBy?: string;
}

interface ControlInput extends ControlStateProps {
  id?: string;
  required?: boolean;
  'aria-describedby'?: string;
  'aria-errormessage'?: string;
}

function resolveControl(field: FieldControlContextValue | null, input: ControlInput) {
  const invalid = input.invalid ?? field?.invalid ?? false;
  const valid = input.valid ?? field?.valid ?? false;
  return {
    attributes: {
      id: input.id ?? field?.id,
      required: input.required ?? field?.required,
      'aria-invalid': invalid || undefined,
      'aria-describedby': input['aria-describedby'] ?? input.describedBy ?? field?.describedBy,
      'aria-errormessage': input['aria-errormessage'] ?? (invalid ? field?.errorId : undefined),
    },
    stateClass: invalid ? 'border-danger focus:border-danger' : valid ? 'border-status-published focus:border-status-published' : undefined,
  };
}

export interface InputProps extends InputHTMLAttributes<HTMLInputElement>, ControlStateProps {}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { invalid, valid, describedBy, className, id, required, ...rest },
  ref,
) {
  const field = useContext(FieldControlContext);
  const { attributes, stateClass } = resolveControl(field, {
    id, invalid, valid, describedBy, required,
    'aria-describedby': rest['aria-describedby'],
    'aria-errormessage': rest['aria-errormessage'],
  });
  return <input ref={ref} {...rest} {...attributes} className={cn(controlClass, 'min-h-12', stateClass, className)} />;
});

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement>, ControlStateProps {}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { invalid, valid, describedBy, className, id, required, ...rest },
  ref,
) {
  const field = useContext(FieldControlContext);
  const { attributes, stateClass } = resolveControl(field, {
    id, invalid, valid, describedBy, required,
    'aria-describedby': rest['aria-describedby'],
    'aria-errormessage': rest['aria-errormessage'],
  });
  return <textarea ref={ref} {...rest} {...attributes} className={cn(controlClass, 'min-h-28 resize-y leading-relaxed', stateClass, className)} />;
});

export const Checkbox = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Checkbox(
  { className, ...props },
  ref,
) {
  return (
    <span className={cn('relative inline-flex h-5 w-5 shrink-0', className)}>
      <input
        ref={ref}
        type="checkbox"
        className="peer h-5 w-5 cursor-pointer appearance-none rounded-md border border-line-strong bg-background transition-colors duration-snap checked:border-ink checked:bg-ink disabled:cursor-not-allowed disabled:bg-surface-2"
        {...props}
      />
      <CheckIcon className="pointer-events-none absolute inset-0 m-auto h-3.5 w-3.5 text-ink-fg opacity-0 transition-opacity peer-checked:opacity-100" strokeWidth={3} aria-hidden="true" />
    </span>
  );
});
