import type { ComponentType } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRightIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { Skeleton } from './Skeleton';
import { useAnimatedNumber } from '../../hooks/useAnimatedNumber';

interface StatCardProps {
  label: string;
  value: number;
  hint?: string;
  icon: ComponentType<{ className?: string }>;
  to?: string;
  loading?: boolean;
  className?: string;
}

export function StatCard({ label, value, hint, icon: Icon, to, loading = false, className }: StatCardProps) {
  const animated = useAnimatedNumber(loading ? 0 : value);
  const body = (
    <>
      <div className="flex items-start justify-between gap-3">
        <span className="micro text-secondary">{label}</span>
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-pill bg-surface text-foreground">
          <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
        </span>
      </div>
      {loading ? (
        <Skeleton className="mt-4 h-9 w-20" />
      ) : (
        <p className="mt-4 text-[2.25rem] font-semibold leading-none tabular-nums tracking-[-0.02em] text-foreground">
          <span aria-hidden="true">{animated}</span>
          <span className="sr-only">{value}</span>
        </p>
      )}
      <div className="mt-3 flex items-center justify-between gap-3 text-sm text-secondary">
        <span className="truncate">{hint}</span>
        {to && <ArrowUpRightIcon className="h-4 w-4 shrink-0 transition-transform duration-snap ease-snap motion-safe:group-hover:rotate-45 rtl:-scale-x-100" aria-hidden="true" />}
      </div>
    </>
  );
  const shell = cn('group relative block rounded-card-sm border border-line bg-background p-5 transition-transform duration-snap ease-snap', to && 'motion-safe:fine:hover:-translate-y-1', className);
  return to ? <Link to={to} className={shell}>{body}</Link> : <div className={shell}>{body}</div>;
}
