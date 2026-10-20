import * as RadixSelect from '@radix-ui/react-select';
import { forwardRef, useContext, type ReactNode } from 'react';
import { CheckIcon, ChevronDownIcon, ChevronUpIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { FieldControlContext } from './fieldContext';
import { menuItemClass, menuSurfaceClass } from './menuStyles';

const EMPTY_VALUE = '__select_empty__';

export interface SelectOption {
  value: string;
  label: string;
  description?: string;
  icon?: ReactNode;
  disabled?: boolean;
}

export interface SelectProps {
  value: string;
  onValueChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  id?: string;
  name?: string;
  disabled?: boolean;
  required?: boolean;
  invalid?: boolean;
  valid?: boolean;
  describedBy?: string;
  className?: string;
  contentClassName?: string;
  align?: 'start' | 'center' | 'end';
  size?: 'md' | 'sm';
  leadingIcon?: ReactNode;
  onBlur?: () => void;
  'aria-label'?: string;
  'aria-labelledby'?: string;
}

const toInternal = (value: string) => (value === '' ? EMPTY_VALUE : value);
const fromInternal = (value: string) => (value === EMPTY_VALUE ? '' : value);

const iconSlotClass = 'shrink-0 text-secondary [&>svg]:h-4 [&>svg]:w-4';

export const Select = forwardRef<HTMLButtonElement, SelectProps>(function Select(
  {
    value,
    onValueChange,
    options,
    placeholder,
    id,
    name,
    disabled,
    required,
    invalid,
    valid,
    describedBy,
    className,
    contentClassName,
    align = 'start',
    size = 'md',
    leadingIcon,
    onBlur,
    ...aria
  },
  ref,
) {
  const field = useContext(FieldControlContext);
  const isInvalid = invalid ?? field?.invalid ?? false;
  const isValid = !isInvalid && (valid ?? field?.valid ?? false);
  const hasEmptyOption = options.some((option) => option.value === '');
  const internalValue = value === '' && !hasEmptyOption ? '' : toInternal(value);
  const selected = options.find((option) => option.value === value);

  return (
    <RadixSelect.Root
      value={internalValue}
      onValueChange={(next) => onValueChange(fromInternal(next))}
      disabled={disabled}
      required={required ?? field?.required}
      name={name}
    >
      <RadixSelect.Trigger
        ref={ref}
        id={id ?? field?.id}
        aria-invalid={isInvalid || undefined}
        aria-describedby={describedBy ?? field?.describedBy}
        aria-errormessage={isInvalid ? field?.errorId : undefined}
        onBlur={onBlur}
        className={cn(
          'group inline-flex w-full min-w-0 items-center gap-2 rounded-control border border-line bg-surface-field text-start text-foreground transition-[border-color,background-color] duration-snap ease-snap data-[state=open]:border-line-strong data-[state=open]:bg-background data-[placeholder]:text-secondary disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-secondary',
          size === 'md' ? 'min-h-12 px-4 py-3 text-base' : 'min-h-10 px-3.5 py-2 text-sm',
          isInvalid && 'border-danger',
          isValid && 'border-status-published',
          className,
        )}
        {...aria}
      >
        {leadingIcon && <span className={iconSlotClass} aria-hidden="true">{leadingIcon}</span>}
        <span className="min-w-0 flex-1 truncate">
          <RadixSelect.Value placeholder={placeholder}>{selected?.label ?? (value || undefined)}</RadixSelect.Value>
        </span>
        <RadixSelect.Icon asChild>
          <ChevronDownIcon
            className="h-4 w-4 shrink-0 text-secondary transition-transform duration-snap ease-snap group-data-[state=open]:rotate-180"
            aria-hidden="true"
          />
        </RadixSelect.Icon>
      </RadixSelect.Trigger>
      <RadixSelect.Portal>
        <RadixSelect.Content
          position="popper"
          align={align}
          sideOffset={6}
          collisionPadding={12}
          className={cn(
            menuSurfaceClass,
            'min-w-[var(--radix-select-trigger-width)] max-w-[min(var(--radix-select-content-available-width),28rem)]',
            contentClassName,
          )}
        >
          <RadixSelect.ScrollUpButton className="flex h-6 cursor-default items-center justify-center text-secondary">
            <ChevronUpIcon className="h-4 w-4" aria-hidden="true" />
          </RadixSelect.ScrollUpButton>
          <RadixSelect.Viewport className="max-h-[min(var(--radix-select-content-available-height),22rem)]">
            {options.map((option) => (
              <RadixSelect.Item
                key={option.value}
                value={toInternal(option.value)}
                disabled={option.disabled}
                data-value={option.value}
                className={cn(menuItemClass, 'pe-9 data-[state=checked]:font-medium')}
              >
                {option.icon && <span className={iconSlotClass} aria-hidden="true">{option.icon}</span>}
                <span className="block min-w-0 flex-1 truncate">
                  <RadixSelect.ItemText>{option.label}</RadixSelect.ItemText>
                </span>
                {option.description && <span className="shrink-0 text-xs text-secondary">{option.description}</span>}
                <RadixSelect.ItemIndicator className="absolute end-2.5 inline-flex h-4 w-4 items-center justify-center text-foreground">
                  <CheckIcon className="h-4 w-4" strokeWidth={2.5} aria-hidden="true" />
                </RadixSelect.ItemIndicator>
              </RadixSelect.Item>
            ))}
          </RadixSelect.Viewport>
          <RadixSelect.ScrollDownButton className="flex h-6 cursor-default items-center justify-center text-secondary">
            <ChevronDownIcon className="h-4 w-4" aria-hidden="true" />
          </RadixSelect.ScrollDownButton>
        </RadixSelect.Content>
      </RadixSelect.Portal>
    </RadixSelect.Root>
  );
});
