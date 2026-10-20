import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useI18n } from '../../contexts/i18n';
import { PublicHeader } from './PublicHeader';
import { PublicFooter } from './PublicFooter';
import { SupportReplyNotifier } from '../../features/help/SupportReplyNotifier';

export function PublicLayout() {
  const location = useLocation();
  const { t } = useI18n();
  const home = location.pathname === '/';

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      if (location.hash) {
        let targetId = location.hash.slice(1);
        try {
          targetId = decodeURIComponent(targetId);
        } catch {
          targetId = '';
        }
        const target = targetId ? document.getElementById(targetId) : null;
        if (target) {
          target.scrollIntoView({
            behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
            block: 'start',
          });
          target.focus({ preventScroll: true });
          return;
        }
      }
      window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
      document.getElementById('main-content')?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [location.hash, location.pathname]);

  return (
    <div className="flex min-h-dvh w-full flex-col bg-background text-foreground">
      <a
        href="#main-content"
        className="fixed start-5 top-3 z-[90] -translate-y-24 rounded-pill bg-ink px-5 py-3 text-sm font-medium text-ink-fg transition-transform focus:translate-y-0 print:hidden"
      >
        {t('a11y.skipToContent', 'Skip to main content')}
      </a>
      <PublicHeader transparent={home} />
      <main id="main-content" tabIndex={-1} className="flex-1 focus:outline-none">
        <Outlet />
      </main>
      <PublicFooter />
      <SupportReplyNotifier />
    </div>
  );
}
