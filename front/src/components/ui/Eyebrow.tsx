import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface EyebrowProps {
  children: ReactNode;
  onInk?: boolean;
  className?: string;
}

export function Eyebrow({ children, onInk = false, className }: EyebrowProps) {
  return (
    <span className={cn('eyebrow inline-flex items-center gap-2 text-sm font-medium', onInk ? 'text-ink-secondary' : 'text-secondary', className)}>
      <span className="h-1.5 w-1.5 shrink-0 rounded-pill bg-accent" aria-hidden="true" />
      {children}
    </span>
  );
}
