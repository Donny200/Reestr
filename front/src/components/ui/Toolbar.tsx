import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface ToolbarProps {
  summary?: ReactNode;
  children?: ReactNode;
  className?: string;
}

export function Toolbar({ summary, children, className }: ToolbarProps) {
  return (
    <div className={cn('flex flex-col gap-3 border-b border-line px-5 py-3 sm:flex-row sm:items-center sm:justify-between', className)}>
      {summary !== undefined && <p className="text-sm text-secondary tabular-nums" aria-live="polite">{summary}</p>}
      {children && <div className="grid gap-2 sm:flex sm:items-center [&>*]:min-w-0">{children}</div>}
    </div>
  );
}
