import { useEffect, useId, useRef, useState, type ComponentType, type CSSProperties } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  ArrowLeftIcon,
  BarChart3Icon,
  BuildingIcon,
  CalendarDaysIcon,
  DatabaseIcon,
  FileTextIcon,
  FlagIcon,
  GlobeIcon,
  KeyIcon,
  LayoutDashboardIcon,
  LockIcon,
  MessagesSquareIcon,
  LogOutIcon,
  MenuIcon,
  ShieldCheckIcon,
  UsersIcon,
  XIcon,
} from 'lucide-react';
import { cn } from '../../lib/cn';
import { Logo } from './Logo';
import { LanguageSwitcher } from './LanguageSwitcher';
import { navForRole, type AdminNavItem } from './adminNav';
import { ThemeToggle } from '../ui/ThemeToggle';
import { Button } from '../ui/Button';
import { Avatar } from '../ui/Avatar';
import { FOCUSABLE, trapTab, useBodyScrollLock } from '../ui/dialogUtils';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '../ui/DropdownMenu';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { HELP_VIEW_PERMISSION } from '../../features/help/helpOptions';
import { useLiveSupport } from '../../features/help/liveSupport';
import { useHelpInboxUnread } from '../../features/help/queries';
import { fullName, roleLabel } from '../../utils/format';

const icons: Record<AdminNavItem['icon'], ComponentType<{ className?: string }>> = {
  functions: FileTextIcon,
  dashboard: LayoutDashboardIcon,
  building: BuildingIcon,
  shield: ShieldCheckIcon,
  users: UsersIcon,
  key: KeyIcon,
  globe: GlobeIcon,
  legacy: DatabaseIcon,
  lock: LockIcon,
  reports: FlagIcon,
  analytics: BarChart3Icon,
  questions: MessagesSquareIcon,
  calendar: CalendarDaysIcon,
};

const TOP_BAR_HEIGHT = '4.5rem';

function SidebarNav({ items, onNavigate, badges = {} }: { items: AdminNavItem[]; onNavigate?: () => void; badges?: Record<string, number> }) {
  const { t } = useI18n();
  return (
    <nav aria-label={t('nav.admin')} className="flex-1 overflow-y-auto px-3 py-4">
      <ul className="space-y-1">
        {items.map((item) => {
          const Icon = icons[item.icon];
          return (
            <li key={item.to} className={cn(item.dividerBefore && 'mt-3 border-t border-ink-line pt-3')}>
              <NavLink
                to={item.to}
                end={item.end}
                onClick={onNavigate}
                className={({ isActive }) =>
                  cn(
                    'group flex min-h-11 items-center gap-3 rounded-pill px-4 text-sm font-medium transition-colors duration-snap ease-snap',
                    isActive ? 'bg-ink-hover text-ink-fg' : 'text-ink-secondary fine:hover:bg-ink-hover fine:hover:text-ink-fg',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden="true" />
                    <span className="min-w-0 flex-1 truncate">{t(item.labelKey)}</span>
                    {(badges[item.to] ?? 0) > 0 && (
                      <span className="rounded-pill bg-accent px-2 py-0.5 text-xs font-semibold tabular-nums text-ink" aria-label={`${badges[item.to]} ${t('support.unreadConversations', 'conversations with unread messages')}`}>
                        {badges[item.to]}
                      </span>
                    )}
                    {isActive && <span className="h-1.5 w-1.5 shrink-0 rounded-pill bg-accent" aria-hidden="true" />}
                  </>
                )}
              </NavLink>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

function SidebarFooter() {
  const { t } = useI18n();
  return (
    <div className="border-t border-ink-line p-3">
      <Link to="/" className="flex min-h-11 items-center gap-3 rounded-pill px-4 text-sm font-medium text-ink-secondary transition-colors duration-snap fine:hover:bg-ink-hover fine:hover:text-ink-fg">
        <ArrowLeftIcon className="h-4 w-4 rtl:-scale-x-100" aria-hidden="true" />
        <span>{t('nav.backToSite')}</span>
      </Link>
    </div>
  );
}

export function AdminLayout() {
  const { t } = useI18n();
  const { user, signOut } = useAuth();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const drawerRef = useRef<HTMLDivElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const drawerTitleId = useId();
  const items = navForRole(user?.role, user?.permissions);
  const navigate = useNavigate();
  const canViewSupport = user?.permissions.includes(HELP_VIEW_PERMISSION) ?? false;
  useLiveSupport('staff', canViewSupport, user?.id ?? null);
  const supportUnreadData = useHelpInboxUnread(canViewSupport).data;
  const supportUnread = supportUnreadData?.conversations ?? 0;
  const badges = { '/admin/questions': supportUnread };

  // A new unread visitor message is announced once (not on the inbox itself, where the list already shows it).
  const knownUnreadMessages = useRef<number | null>(null);
  useEffect(() => {
    const messages = supportUnreadData?.messages;
    if (messages === undefined) return;
    const previous = knownUnreadMessages.current;
    knownUnreadMessages.current = messages;
    if (previous !== null && messages > previous && location.pathname !== '/admin/questions') {
      toast.info(t('support.newVisitorMessage', 'New message in the support inbox'), {
        action: { label: t('support.openInbox', 'Open inbox'), onClick: () => navigate('/admin/questions') },
      });
    }
  }, [supportUnreadData?.messages, location.pathname, navigate, t]);

  useEffect(() => setDrawerOpen(false), [location.pathname]);
  useBodyScrollLock(drawerOpen);
  useEffect(() => {
    if (!drawerOpen) return undefined;
    const frame = window.requestAnimationFrame(() => drawerRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus());
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setDrawerOpen(false);
        window.requestAnimationFrame(() => menuButtonRef.current?.focus());
        return;
      }
      if (event.key === 'Tab' && drawerRef.current) trapTab(event, drawerRef.current);
    };
    document.addEventListener('keydown', onKey);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', onKey);
    };
  }, [drawerOpen]);

  const closeDrawer = () => {
    setDrawerOpen(false);
    window.requestAnimationFrame(() => menuButtonRef.current?.focus());
  };

  const currentItem = [...items].reverse().find((item) => (item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)));
  const pageTitle = location.pathname.startsWith('/admin/functions/') ? t('nav.functions') : currentItem ? t(currentItem.labelKey) : t('nav.admin');

  return (
    <div className="flex min-h-dvh w-full bg-surface text-foreground">
      <aside aria-label={t('nav.admin')} className="ink hidden w-64 shrink-0 bg-ink text-ink-fg lg:block">
        <div className="sticky top-0 flex h-dvh w-64 flex-col">
          <div className="flex h-[4.5rem] items-center border-b border-ink-line px-6">
            <Logo to="/admin" onInk />
          </div>
          <SidebarNav badges={badges} items={items} />
          <SidebarFooter />
        </div>
      </aside>

      {drawerOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            tabIndex={-1}
            aria-label={t('action.close')}
            onClick={closeDrawer}
            className="absolute inset-0 h-full w-full cursor-default bg-[var(--backdrop)] backdrop-blur-[16px] animate-fade-in"
          />
          <div
            ref={drawerRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={drawerTitleId}
            className="ink relative z-10 flex h-full w-[min(18rem,88vw)] flex-col bg-ink text-ink-fg animate-fade-in"
          >
            <div className="flex h-[4.5rem] items-center justify-between border-b border-ink-line px-5">
              <div id={drawerTitleId} className="min-w-0"><Logo to="/admin" onInk /></div>
              <Button variant="ghost" size="iconSm" className="text-ink-fg fine:hover:bg-ink-hover fine:hover:text-ink-fg" aria-label={t('action.close')} onClick={closeDrawer}>
                <XIcon aria-hidden="true" />
              </Button>
            </div>
            <SidebarNav badges={badges} items={items} onNavigate={() => setDrawerOpen(false)} />
            <SidebarFooter />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-[4.5rem] items-center justify-between gap-3 border-b border-line bg-surface px-5 sm:px-8">
          <div className="flex min-w-0 items-center gap-2">
            <Button
              ref={menuButtonRef}
              variant="outline"
              size="icon"
              className="lg:hidden"
              onClick={() => setDrawerOpen(true)}
              aria-label={t('nav.admin')}
              aria-expanded={drawerOpen}
              aria-haspopup="dialog"
            >
              <MenuIcon aria-hidden="true" />
            </Button>
            <div className="min-w-0">
              <p className="micro hidden text-secondary sm:block">{t('nav.admin')}</p>
              <p className="truncate text-base font-semibold text-foreground">{pageTitle}</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <ThemeToggle />
            <LanguageSwitcher compact />
            {user && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    type="button"
                    className="flex min-h-10 items-center gap-2 rounded-pill border border-line bg-background ps-1 pe-1 transition-colors duration-snap fine:hover:bg-surface-2 md:pe-4"
                    aria-label={fullName(user)}
                  >
                    <Avatar user={user} size="sm" />
                    <span className="hidden min-w-0 text-start md:block">
                      <span className="block max-w-40 truncate text-sm font-medium leading-tight text-foreground">{fullName(user)}</span>
                      <span className="block max-w-40 truncate text-xs leading-tight text-secondary">{roleLabel(user.role, t)}</span>
                    </span>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent className="w-64">
                  <DropdownMenuLabel>
                    <span className="block truncate text-sm font-medium text-foreground">{fullName(user)}</span>
                    <span className="block truncate font-normal">{roleLabel(user.role, t)} · {user.email}</span>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link to="/settings/security"><LockIcon />{t('nav.security')}</Link>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem destructive onSelect={() => void signOut()}>
                    <LogOutIcon />{t('action.logout')}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            )}
          </div>
        </header>

        <main className="flex-1 px-5 py-6 sm:px-8 sm:py-8" style={{ '--sticky-top': TOP_BAR_HEIGHT } as CSSProperties}>
          <div className="mx-auto w-full max-w-shell">
            {user?.mfa?.required && !user.mfa.enrolled && !location.pathname.startsWith('/settings/security') && (
              <div role="status" className="mb-6 flex flex-col gap-3 rounded-control bg-status-pending-bg px-4 py-3 text-sm leading-6 text-status-pending sm:flex-row sm:items-center sm:justify-between">
                <span>
                  <ShieldCheckIcon className="me-2 inline h-4 w-4 align-text-bottom" aria-hidden="true" />
                  {user.mfa.enrollmentDeadline
                    ? `${t('mfa.bannerDeadline', 'Your role requires two-factor authentication. Set it up before')} ${new Date(user.mfa.enrollmentDeadline).toLocaleDateString()}.`
                    : t('mfa.banner', 'Your role requires two-factor authentication. Set it up now.')}
                </span>
                <Link to="/settings/security#mfa" className="shrink-0 font-semibold underline-offset-4 fine:hover:underline">{t('mfa.setUp', 'Set up now')}</Link>
              </div>
            )}
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
