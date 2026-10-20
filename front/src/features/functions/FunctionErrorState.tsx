import { LockIcon } from 'lucide-react';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { useI18n } from '../../contexts/i18n';
import { statusOf } from '../../utils/errors';

export function FunctionErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n();
  if (statusOf(error) === 403) {
    return <EmptyState title={t('state.forbidden')} icon={<LockIcon className="h-6 w-6" aria-hidden="true" />} />;
  }
  return <ErrorState error={error} onRetry={onRetry} />;
}
