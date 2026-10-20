import { ArrowUpRightIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { homeRouteForRole, useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { Logo } from './Logo';

const linkClass = 'group inline-flex min-h-10 items-center gap-1.5 text-sm text-ink-secondary transition-colors duration-snap fine:hover:text-ink-fg';

export function PublicFooter() {
  const { t } = useI18n();
  const { user } = useAuth();

  return (
    <footer className="ink relative mt-8 overflow-hidden rounded-t-card bg-ink text-ink-fg print:hidden">
      <span className="watermark" aria-hidden="true">{t('app.name')}</span>
      <div className="shell relative grid gap-10 py-14 sm:py-16 md:grid-cols-[minmax(0,1.5fr)_1fr_1fr]">
        <div className="max-w-md">
          <Logo onInk />
          <p className="mt-5 max-w-sm text-sm leading-6 text-ink-secondary">{t('app.tagline')}</p>
        </div>

        <nav aria-label={t('nav.catalog')}>
          <p className="micro text-ink-secondary">{t('nav.catalog')}</p>
          <ul className="mt-3 space-y-1">
            <li><Link to="/" className={linkClass}>{t('nav.home')}</Link></li>
            <li><Link to="/#organizations" className={linkClass}>{t('nav.organizations')}</Link></li>
            <li><Link to="/#functions" className={linkClass}>{t('nav.functions')}</Link></li>
            <li><Link to="/finder" className={linkClass}>{t('nav.finder', 'Service finder')}</Link></li>
            <li><Link to="/saved" className={linkClass}>{t('nav.saved', 'Saved')}</Link></li>
            <li><Link to="/support" className={linkClass}>{t('nav.support', 'Support')}</Link></li>
          </ul>
        </nav>

        <nav aria-label={t('nav.admin')}>
          <p className="micro text-ink-secondary">{t('nav.admin')}</p>
          <ul className="mt-3 space-y-1">
            <li>
              <Link to={user ? homeRouteForRole(user.role) : '/login'} className={linkClass}>
                {user ? t('nav.admin') : t('action.login')}
                <ArrowUpRightIcon
                  className="h-3.5 w-3.5 transition-transform duration-snap motion-safe:group-hover:rotate-45 rtl:-scale-x-100"
                  aria-hidden="true"
                />
              </Link>
            </li>
            <li><Link to="/settings/security" className={linkClass}>{t('nav.security')}</Link></li>
          </ul>
        </nav>
      </div>

      <div className="relative border-t border-ink-line">
        <div className="shell flex flex-col gap-2 py-5 text-sm leading-5 text-ink-secondary sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} {t('app.name')}</p>
          <p>{t('app.demoNotice')}</p>
        </div>
      </div>
    </footer>
  );
}
