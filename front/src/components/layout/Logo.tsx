import { Link } from 'react-router-dom';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';

interface LogoProps {
  to?: string;
  onInk?: boolean;
  className?: string;
}

export function Logo({ to = '/', onInk = false, className }: LogoProps) {
  const { t } = useI18n();
  return (
    <Link
      to={to}
      aria-label={t('app.name')}
      className={cn('inline-flex min-w-0 items-center gap-2.5 rounded-pill text-base font-semibold tracking-[-0.01em]', onInk ? 'text-ink-fg' : 'text-foreground', className)}
    >
      <span className="h-2.5 w-2.5 shrink-0 rounded-pill bg-accent" aria-hidden="true" />
      <span className="truncate">{t('app.name')}</span>
    </Link>
  );
}
