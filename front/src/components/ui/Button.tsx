import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import type { VariantProps } from 'class-variance-authority';
import { ArrowRightIcon, ArrowUpRightIcon, Loader2Icon } from 'lucide-react';
import { arrowBadgeVariants, buttonVariants } from './buttonVariants';
import { cn } from '../../lib/cn';

export type ButtonArrow = 'right' | 'up-right';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, Omit<VariantProps<typeof buttonVariants>, 'arrow'> {
  loading?: boolean;
  icon?: ReactNode;
  arrow?: ButtonArrow;
}

export function ArrowBadge({ arrow, variant }: { arrow: ButtonArrow; variant: VariantProps<typeof arrowBadgeVariants>['variant'] }) {
  const Icon = arrow === 'right' ? ArrowRightIcon : ArrowUpRightIcon;
  return (
    <span className={arrowBadgeVariants({ variant })} aria-hidden="true">
      <Icon strokeWidth={2} />
    </span>
  );
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant, size, loading = false, icon, arrow, disabled, type = 'button', children, ...rest },
  ref,
) {
  const inactive = Boolean(disabled) || loading;
  return (
    <button
      ref={ref}
      type={type}
      disabled={inactive}
      aria-disabled={inactive || undefined}
      aria-busy={loading || undefined}
      className={cn(buttonVariants({ variant, size, arrow: arrow ? true : undefined }), className)}
      {...rest}
    >
      {loading && (
        <span className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
          <Loader2Icon className="animate-spin motion-reduce:animate-none" />
        </span>
      )}
      <span className={cn('inline-flex items-center justify-center gap-2', loading && 'opacity-0')}>
        {icon && <span className="inline-flex shrink-0 [&_svg]:rtl:-scale-x-100" aria-hidden="true">{icon}</span>}
        {children}
      </span>
      {arrow && <span className={cn(loading && 'opacity-0')}><ArrowBadge arrow={arrow} variant={variant ?? 'dark'} /></span>}
    </button>
  );
});
