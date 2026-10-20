import type { ReactNode } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';
import { functionStatusLabel, type FunctionStatus } from '../../types/adminFunctions';
import type { VerificationStatus } from '../../types/api';
import { verificationLabels } from '../../utils/verification';

const badgeVariants = cva(
  'inline-flex max-w-full items-center gap-1.5 rounded-pill border px-4 py-2 text-sm font-medium leading-5 [&_svg]:h-3.5 [&_svg]:w-3.5 [&_svg]:shrink-0',
  {
    variants: {
      tone: {
        neutral: 'border-line bg-transparent text-foreground',
        accent: 'border-accent/40 bg-transparent text-accent-text',
        onInk: 'border-ink-line bg-transparent text-ink-fg',
      },
      size: {
        sm: 'px-2.5 py-0.5 text-xs',
        md: '',
      },
    },
    defaultVariants: { tone: 'neutral', size: 'md' },
  },
);

interface BadgeProps extends VariantProps<typeof badgeVariants> {
  className?: string;
  children: ReactNode;
}

export function Badge({ tone, size, className, children }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone, size }), className)}>{children}</span>;
}

export type StatusTone = 'draft' | 'pending' | 'published' | 'danger';

const statusTones: Record<StatusTone, string> = {
  draft: 'bg-status-draft-bg text-status-draft',
  pending: 'bg-status-pending-bg text-status-pending',
  published: 'bg-status-published-bg text-status-published',
  danger: 'bg-status-danger-bg text-status-danger',
};

export function StatusPill({ tone, className, children }: { tone: StatusTone; className?: string; children: ReactNode }) {
  return (
    <span className={cn('inline-flex max-w-full items-center gap-1.5 whitespace-nowrap rounded-pill px-2.5 py-1 text-xs font-medium leading-4', statusTones[tone], className)}>
      <span className="h-1.5 w-1.5 shrink-0 rounded-pill bg-current" aria-hidden="true" />
      {children}
    </span>
  );
}

export function StatusBadge({ enabled }: { enabled: boolean }) {
  const { t } = useI18n();
  return <StatusPill tone={enabled ? 'published' : 'draft'}>{enabled ? t('status.active') : t('status.inactive')}</StatusPill>;
}

const functionTones: Record<FunctionStatus, StatusTone> = {
  DRAFT: 'draft',
  PENDING_REVIEW: 'pending',
  PUBLISHED: 'published',
  DEACTIVATED: 'danger',
};

export function FunctionStatusBadge({ status }: { status: FunctionStatus }) {
  const { t } = useI18n();
  return <StatusPill tone={functionTones[status]}>{functionStatusLabel(status, t)}</StatusPill>;
}

const verificationTones: Record<VerificationStatus, StatusTone> = {
  UNVERIFIED: 'draft',
  VERIFIED: 'published',
  DUE: 'pending',
  OUTDATED: 'danger',
};

export function VerificationBadge({ status }: { status: VerificationStatus | undefined }) {
  const { t } = useI18n();
  const value = status ?? 'UNVERIFIED';
  return <StatusPill tone={verificationTones[value]}>{t(`verification.status.${value}`, verificationLabels[value])}</StatusPill>;
}
