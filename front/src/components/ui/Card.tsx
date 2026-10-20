import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRightIcon } from 'lucide-react';
import { cn } from '../../lib/cn';

interface CardProps {
  className?: string;
  children: ReactNode;
  size?: 'sm' | 'lg';
  id?: string;
}

export function Card({ className, children, size = 'sm', id }: CardProps) {
  return (
    <section id={id} className={cn('border border-line bg-background', size === 'lg' ? 'rounded-card' : 'rounded-card-sm', id && 'scroll-mt-24', className)}>
      {children}
    </section>
  );
}

interface CardHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  icon?: ReactNode;
  className?: string;
}

export function CardHeader({ title, description, actions, icon, className }: CardHeaderProps) {
  return (
    <header className={cn('flex flex-col gap-3 border-b border-line px-5 py-4 sm:flex-row sm:items-center sm:justify-between', className)}>
      <div className="flex min-w-0 items-start gap-3">
        {icon && (
          <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-pill bg-surface text-foreground [&_svg]:h-4 [&_svg]:w-4" aria-hidden="true">
            {icon}
          </span>
        )}
        <div className="min-w-0">
          <h2 className="text-base font-semibold text-foreground">{title}</h2>
          {description && <p className="mt-0.5 text-sm text-secondary">{description}</p>}
        </div>
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

export function CardBody({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('px-5 py-5', className)}>{children}</div>;
}

export function CardFooter({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <footer className={cn('flex flex-wrap items-center justify-end gap-2 border-t border-line bg-surface/60 px-5 py-3', className)}>
      {children}
    </footer>
  );
}

interface InkCardProps {
  className?: string;
  children: ReactNode;
  to?: string;
  ariaLabel?: string;
}

const inkCardClass = 'ink relative block overflow-hidden rounded-card bg-ink text-ink-fg ring-1 ring-ink-ring';

export function InkCard({ className, children, to, ariaLabel }: InkCardProps) {
  if (!to) return <div className={cn(inkCardClass, className)}>{children}</div>;
  return (
    <Link
      to={to}
      aria-label={ariaLabel}
      className={cn(
        inkCardClass,
        'group transition-transform duration-snap ease-snap motion-safe:fine:hover:-translate-y-2 motion-safe:fine:hover:scale-[1.012]',
        className,
      )}
    >
      {children}
    </Link>
  );
}

export function InkCardArrow({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'flex h-10 w-10 shrink-0 items-center justify-center rounded-pill bg-background text-ink transition-transform duration-snap ease-snap motion-safe:group-hover:rotate-45',
        className,
      )}
      aria-hidden="true"
    >
      <ArrowUpRightIcon className="h-5 w-5 rtl:-scale-x-100" strokeWidth={2} />
    </span>
  );
}
