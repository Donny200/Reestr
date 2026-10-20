import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeftIcon } from 'lucide-react';
import { Logo } from './Logo';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ThemeToggle } from '../ui/ThemeToggle';
import { Eyebrow } from '../ui/Eyebrow';
import { useI18n } from '../../contexts/i18n';

/** Shared two-pane frame for the sign-in and password-reset pages. */
export function AuthShell({ children, backLink = true }: { children: ReactNode; backLink?: boolean }) {
  const { t } = useI18n();
  return (
    <div className="flex min-h-dvh w-full bg-surface text-foreground">
      <a
        href="#login-main"
        className="fixed start-5 top-3 z-50 -translate-y-24 rounded-pill bg-ink px-5 py-3 text-sm font-medium text-ink-fg transition-transform focus:translate-y-0"
      >
        {t('a11y.skipToContent', 'Skip to main content')}
      </a>

      <aside className="ink relative hidden w-[44%] shrink-0 overflow-hidden bg-ink text-ink-fg lg:block">
        <span className="watermark" aria-hidden="true">{t('app.name')}</span>
        <div className="relative flex h-full flex-col justify-between p-10 xl:p-14">
          <Logo onInk />
          <div className="max-w-md">
            <Eyebrow onInk>{t('nav.admin')}</Eyebrow>
            <p className="mt-5 text-3xl font-semibold leading-tight tracking-[-0.02em] xl:text-4xl">{t('home.ctaText')}</p>
          </div>
          <p className="max-w-md text-sm leading-6 text-ink-secondary">{t('app.demoNotice')}</p>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-20 items-center justify-between gap-3 px-5 sm:px-8">
          <div className="min-w-0 lg:hidden">
            <Logo />
          </div>
          <div className="ms-auto flex items-center gap-2">
            <ThemeToggle />
            <LanguageSwitcher compact />
          </div>
        </header>

        <main id="login-main" tabIndex={-1} className="flex flex-1 items-center justify-center px-5 py-10 focus:outline-none sm:px-8 sm:py-14">
          <div className="w-full max-w-md">
            <div className="rounded-card border border-line bg-background p-6 sm:p-8">{children}</div>
            {backLink && (
              <Link to="/" className="mt-6 inline-flex min-h-10 items-center gap-2 rounded-pill text-sm font-medium text-secondary transition-colors duration-snap fine:hover:text-foreground">
                <ArrowLeftIcon className="h-4 w-4 rtl:-scale-x-100" aria-hidden="true" />
                {t('nav.backToSite')}
              </Link>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
