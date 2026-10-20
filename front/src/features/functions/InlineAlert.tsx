import type { ReactNode } from 'react';
import { AlertTriangleIcon, CheckCircle2Icon, InfoIcon } from 'lucide-react';
import { cn } from '../../lib/cn';

type Tone = 'danger' | 'warning' | 'info' | 'success';

const tones: Record<Tone, string> = {
  danger: 'bg-status-danger-bg text-status-danger',
  warning: 'bg-status-pending-bg text-status-pending',
  info: 'bg-surface text-foreground',
  success: 'bg-status-published-bg text-status-published',
};

const icons = { danger: AlertTriangleIcon, warning: AlertTriangleIcon, info: InfoIcon, success: CheckCircle2Icon };

interface InlineAlertProps {
  tone?: Tone;
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function InlineAlert({ tone = 'info', children, action, className }: InlineAlertProps) {
  const Icon = icons[tone];
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex flex-col gap-3 rounded-control px-4 py-3 text-sm sm:flex-row sm:items-start', tones[tone], className)}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1 leading-relaxed wrap-anywhere">{children}</div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}
