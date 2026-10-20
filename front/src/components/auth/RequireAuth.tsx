import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { LockIcon } from 'lucide-react';
import { useAuth } from '../../contexts/auth';
import type { RoleName } from '../../types/api';
import { EmptyState } from '../ui/States';
import { useI18n } from '../../contexts/i18n';

export function RequireAuth({ roles, anyPermissions }: { roles?: RoleName[]; anyPermissions?: readonly string[] }) {
  const { user, initializing, hasPermission } = useAuth();
  const { t } = useI18n();
  const location = useLocation();

  if (initializing) {
    return (
      <div className="flex min-h-dvh w-full items-center justify-center" role="status" aria-label={t('state.loading')}>
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-line border-t-ink motion-reduce:animate-none" aria-hidden="true" />
      </div>
    );
  }

  if (!user) {
    const from = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" replace state={{ from }} />;
  }

  if (user.mustChangePassword && location.pathname !== '/settings/security') {
    return <Navigate to="/settings/security" replace />;
  }

  if ((roles && !roles.includes(user.role)) || (anyPermissions && !anyPermissions.some(hasPermission))) {
    return (
      <div className="mx-auto w-full max-w-xl px-5 py-16">
        <div className="rounded-card border border-line bg-background">
          <EmptyState title={t('state.forbidden')} icon={<LockIcon className="h-6 w-6" aria-hidden="true" />} />
        </div>
      </div>
    );
  }

  return <Outlet />;
}
