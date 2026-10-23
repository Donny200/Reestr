import type { ReactNode } from 'react';
import { AlertTriangleIcon, InboxIcon, LockIcon, SearchXIcon, WifiOffIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { Button } from './Button';
import { errorMessage, statusOf } from '../../utils/errors';
import { useI18n } from '../../contexts/i18n';

interface StateShellProps {
  icon: ReactNode;
  iconClassName?: string;
  title: string;
  description?: string;
  action?: ReactNode;
  role?: 'status' | 'alert';
}

function StateShell({ icon, iconClassName, title, description, action, role = 'status' }: StateShellProps) {
  return (
    <div className="flex flex-col items-center justify-center px-5 py-14 text-center sm:px-8" role={role} aria-live={role === 'alert' ? 'assertive' : 'polite'}>
      <span className={cn('flex h-14 w-14 items-center justify-center rounded-pill bg-surface text-foreground', iconClassName)}>
        {icon}
      </span>
      <h3 className="mt-5 text-lg font-semibold text-foreground">{title}</h3>
      {description && <p className="mt-1.5 max-w-md text-sm leading-relaxed text-secondary">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function EmptyState({ title, description, action, icon }: { title: string; description?: string; action?: ReactNode; icon?: ReactNode }) {
  return <StateShell icon={icon ?? <InboxIcon className="h-6 w-6" aria-hidden="true" />} title={title} description={description} action={action} />;
}

export function NoResultsState({ title, description }: { title: string; description?: string }) {
  return <EmptyState title={title} description={description} icon={<SearchXIcon className="h-6 w-6" aria-hidden="true" />} />;
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n();
  const status = statusOf(error);
  const forbidden = status === 403;
  const notFound = status === 404;
  const networkFailure = status === 0;
  const title = forbidden
    ? t('state.forbidden')
    : notFound
      ? t('state.notFoundTitle')
      : networkFailure
        ? t('state.networkError', 'No network connection')
        : t('state.errorTitle');

  const detail = errorMessage(error, t);

  return (
    <StateShell
      role="alert"
      icon={forbidden ? <LockIcon className="h-6 w-6" aria-hidden="true" /> : networkFailure ? <WifiOffIcon className="h-6 w-6" aria-hidden="true" /> : <AlertTriangleIcon className="h-6 w-6" aria-hidden="true" />}
      iconClassName={forbidden ? 'bg-status-pending-bg text-status-pending' : networkFailure ? 'bg-status-draft-bg text-status-draft' : 'bg-status-danger-bg text-status-danger'}
      title={title}
      description={detail === title ? undefined : detail}
      action={onRetry && !forbidden ? <Button variant="outline" size="sm" onClick={onRetry}>{t('action.retry')}</Button> : undefined}
    />
  );
}

/**
 * A note the server writes in English for operators (provider errors, skipped texts). The localized state is shown
 * beside it; the note itself stays collapsed and marked as English.
 */
export function TechnicalDetails({ text }: { text: string }) {
  const { t } = useI18n();
  return (
    <details className="mt-1 text-xs text-secondary">
      <summary className="cursor-pointer">{t('state.technicalDetails')}</summary>
      <p className="mt-1 wrap-anywhere" lang="en">{text}</p>
    </details>
  );
}
