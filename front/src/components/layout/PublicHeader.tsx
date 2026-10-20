import { useEffect, useId, useRef, useState, type CSSProperties } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ArrowUpRightIcon, MenuIcon, XIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { Logo } from './Logo';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ThemeToggle } from '../ui/ThemeToggle';
import { Button } from '../ui/Button';
import { buttonVariants } from '../ui/buttonVariants';
import { FOCUSABLE, trapTab, useBodyScrollLock } from '../ui/dialogUtils';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { useSavedServices } from '../../features/saved/useSavedServices';
import { useSupportBadge } from '../../features/help/useSupportBadge';

const links = [
  { to: '/', key: 'nav.home', fallback: 'Home' },
  { to: '/#organizations', key: 'nav.organizations', fallback: 'Organizations' },
  { to: '/#functions', key: 'nav.functions', fallback: 'Services' },
  { to: '/finder', key: 'nav.finder', fallback: 'Service finder' },
  { to: '/saved', key: 'nav.saved', fallback: 'Saved' },
  { to: '/support', key: 'nav.support', fallback: 'Support' },
] as const;

const STAGGER_MS = 45;

export function PublicHeader({ transparent = false }: { transparent?: boolean }) {
  const { t } = useI18n();
  const { user, initializing } = useAuth();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const overlayTitleId = useId();

  useEffect(() => {
    setOpen(false);
  }, [location.hash, location.pathname]);

  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 1024px)');
    const closeOnDesktop = (event: MediaQueryListEvent) => {
      if (event.matches) setOpen(false);
    };
    desktop.addEventListener('change', closeOnDesktop);
    return () => desktop.removeEventListener('change', closeOnDesktop);
  }, []);

  useBodyScrollLock(open);

  useEffect(() => {
    if (!open) return undefined;
    const frame = window.requestAnimationFrame(() => {
      overlayRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    });
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        window.requestAnimationFrame(() => menuButtonRef.current?.focus());
        return;
      }
      if (event.key === 'Tab' && overlayRef.current) trapTab(event, overlayRef.current);
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const savedCount = useSavedServices().items.length;
  const supportUnread = useSupportBadge()?.unread ?? 0;

  const isActive = (to: (typeof links)[number]['to']) => {
    const target = new URL(to, window.location.origin);
    if (target.pathname !== '/') return location.pathname === target.pathname;
    if (location.pathname !== '/') return false;
    if (to === '/') return !location.hash;
    return location.hash === target.hash;
  };

  const linkLabel = (link: (typeof links)[number]) => {
    if (link.to === '/support' && supportUnread > 0) {
      return `${t(link.key, link.fallback)} (${supportUnread} ${t('support.newReplies', 'new')})`;
    }
    const count = link.to === '/saved' ? savedCount : 0;
    return count > 0 ? `${t(link.key, link.fallback)} (${count})` : t(link.key, link.fallback);
  };

  const closeOverlay = () => {
    setOpen(false);
    window.requestAnimationFrame(() => menuButtonRef.current?.focus());
  };

  const closeAfterNavigation = () => {
    setOpen(false);
    window.requestAnimationFrame(() => document.getElementById('main-content')?.focus({ preventScroll: true }));
  };

  const accountLink = (className: string, onClick?: () => void) =>
    initializing ? (
      <span className={cn('h-10 w-28 animate-pulse rounded-pill bg-surface-2 motion-reduce:animate-none', className)} aria-hidden="true" />
    ) : user ? (
      <Link to="/admin" onClick={onClick} className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), className)}>
        {t('nav.admin')}
        <ArrowUpRightIcon className="rtl:-scale-x-100" aria-hidden="true" />
      </Link>
    ) : (
      <Link to="/login" onClick={onClick} className={cn(buttonVariants({ variant: 'dark', size: 'sm' }), className)}>
        {t('action.login')}
      </Link>
    );

  return (
    <>
      <header
        className={cn(
          'z-40 w-full print:hidden',
          transparent ? 'absolute inset-x-0 top-0 bg-transparent' : 'sticky top-0 border-b border-line bg-background',
        )}
      >
        <div className="shell flex h-20 items-center justify-between gap-4">
          <Logo />

          <nav aria-label={t('nav.catalog')} className="hidden items-center gap-1 lg:flex">
            {links.map((link) => {
              const active = isActive(link.to);
              return (
                <Link
                  key={link.to}
                  to={link.to}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'inline-flex min-h-10 items-center rounded-pill px-4 text-sm font-medium transition-colors duration-snap ease-snap',
                    active ? 'bg-ink text-ink-fg' : 'text-secondary fine:hover:bg-surface fine:hover:text-foreground',
                  )}
                >
                  {linkLabel(link)}
                </Link>
              );
            })}
          </nav>

          <div className="flex items-center gap-2">
            <ThemeToggle />
            <LanguageSwitcher compact className="hidden md:block" />
            {accountLink('hidden sm:inline-flex')}
            <Button
              ref={menuButtonRef}
              variant="outline"
              size="icon"
              className="lg:hidden"
              onClick={() => setOpen(true)}
              aria-label={t('a11y.openMenu', 'Open menu')}
              aria-expanded={open}
              aria-controls="public-mobile-menu"
            >
              <MenuIcon aria-hidden="true" />
            </Button>
          </div>
        </div>
      </header>

      {open && (
        <div
          ref={overlayRef}
          id="public-mobile-menu"
          role="dialog"
          aria-modal="true"
          aria-labelledby={overlayTitleId}
          className="ink fixed inset-0 z-[70] flex flex-col bg-ink text-ink-fg animate-fade-in lg:hidden"
        >
          <div className="shell flex h-20 shrink-0 items-center justify-between">
            <div id={overlayTitleId} className="min-w-0">
              <Logo onInk />
            </div>
            <Button variant="ghost" size="icon" className="text-ink-fg fine:hover:bg-ink-hover fine:hover:text-ink-fg" onClick={closeOverlay} aria-label={t('action.close')}>
              <XIcon aria-hidden="true" />
            </Button>
          </div>

          <nav aria-label={t('nav.catalog')} className="shell flex-1 overflow-y-auto py-6">
            <ul className="space-y-1">
              {links.map((link, index) => {
                const active = isActive(link.to);
                return (
                  <li key={link.to} className="animate-rise-in" style={{ animationDelay: `${index * STAGGER_MS}ms` } as CSSProperties}>
                    <Link
                      to={link.to}
                      onClick={closeAfterNavigation}
                      aria-current={active ? 'page' : undefined}
                      className={cn(
                        'flex min-h-14 items-center justify-between gap-4 rounded-card-sm px-4 text-2xl font-medium transition-colors duration-snap',
                        active ? 'bg-ink-hover' : 'fine:hover:bg-ink-hover',
                      )}
                    >
                      {linkLabel(link)}
                      <ArrowUpRightIcon className="h-5 w-5 shrink-0 text-ink-secondary rtl:-scale-x-100" aria-hidden="true" />
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>

          <div
            className="shell flex shrink-0 flex-col gap-3 border-t border-ink-line py-6 animate-rise-in"
            style={{ animationDelay: `${links.length * STAGGER_MS}ms` } as CSSProperties}
          >
            <LanguageSwitcher onInk />
            {!initializing &&
              (user ? (
                <Link to="/admin" onClick={closeAfterNavigation} className={cn(buttonVariants({ variant: 'light', size: 'lg' }), 'w-full')}>
                  {t('nav.admin')}
                  <ArrowUpRightIcon className="rtl:-scale-x-100" aria-hidden="true" />
                </Link>
              ) : (
                <Link to="/login" onClick={closeAfterNavigation} className={cn(buttonVariants({ variant: 'light', size: 'lg' }), 'w-full')}>
                  {t('action.login')}
                </Link>
              ))}
          </div>
        </div>
      )}
    </>
  );
}
