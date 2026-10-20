import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface ActionBarProps {
  children: ReactNode;
  status?: ReactNode;
  className?: string;
}

export function ActionBar({ children, status, className }: ActionBarProps) {
  return (
    <div
      className={cn(
        'sticky bottom-0 z-20 flex flex-col-reverse gap-3 rounded-b-card-sm border-t border-line bg-background/95 px-5 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] backdrop-blur-sm sm:flex-row sm:items-center sm:justify-between',
        className,
      )}
    >
      <div className="flex min-h-6 items-center text-sm text-secondary">{status}</div>
      <div className="flex flex-wrap items-center gap-2 [&>*]:flex-1 sm:[&>*]:flex-none">{children}</div>
    </div>
  );
}
