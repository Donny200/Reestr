import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { CheckIcon, ChevronDownIcon, GlobeIcon, SearchIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';

const SEARCH_THRESHOLD = 7;

interface LanguageSwitcherProps {
  compact?: boolean;
  onInk?: boolean;
  className?: string;
}

export function LanguageSwitcher({ compact = false, onInk = false, className }: LanguageSwitcherProps) {
  const { locale, setLocale, available, t } = useI18n();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const triggerId = useId();
  const listId = useId();

  const known = available.some((item) => item.code.toLowerCase() === locale.toLowerCase());
  const languages = useMemo(() => (known ? available : [{ code: locale, label: locale.toUpperCase() }, ...available]), [known, available, locale]);
  const current = languages.find((item) => item.code.toLowerCase() === locale.toLowerCase());
  const searchable = languages.length > SEARCH_THRESHOLD;
  const term = query.trim().toLowerCase();
  const visible = term ? languages.filter((item) => item.label.toLowerCase().includes(term) || item.code.toLowerCase().includes(term)) : languages;

  const close = (restoreFocus = true) => {
    setOpen(false);
    setQuery('');
    if (restoreFocus) triggerRef.current?.focus();
  };

  const select = (code: string) => {
    setLocale(code);
    close();
  };

  const options = () => Array.from(listRef.current?.querySelectorAll<HTMLLIElement>('[role="option"]') ?? []);

  const focusOption = (index: number) => {
    const items = options();
    if (items.length === 0) return;
    items[(index + items.length) % items.length].focus();
  };

  useEffect(() => {
    if (!open) return undefined;
    const frame = window.requestAnimationFrame(() => {
      if (searchable) searchRef.current?.focus();
      else {
        const selectedIndex = Math.max(0, visible.findIndex((item) => item.code === locale));
        focusOption(selectedIndex);
      }
    });
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) close(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('mousedown', onPointerDown);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const onListKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    const items = options();
    const index = items.indexOf(document.activeElement as HTMLLIElement);
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        focusOption(index + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (index <= 0 && searchable) searchRef.current?.focus();
        else focusOption(index - 1);
        break;
      case 'Home':
        event.preventDefault();
        focusOption(0);
        break;
      case 'End':
        event.preventDefault();
        focusOption(items.length - 1);
        break;
      case 'Escape':
        event.preventDefault();
        close();
        break;
      case 'Tab':
        close(false);
        break;
      default:
        break;
    }
  };

  const onTriggerKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
    }
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        ref={triggerRef}
        id={triggerId}
        type="button"
        aria-label={t('field.language')}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        onClick={() => (open ? close() : setOpen(true))}
        onKeyDown={onTriggerKeyDown}
        className={cn(
          'inline-flex min-h-10 max-w-full items-center gap-2 rounded-pill border px-3.5 text-sm font-medium transition-colors duration-snap ease-snap',
          onInk ? 'border-ink-line text-ink-fg fine:hover:bg-ink-hover' : 'border-line bg-background text-foreground fine:hover:bg-surface',
          compact ? 'w-auto' : 'w-full justify-between',
        )}
      >
        <GlobeIcon className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span className="truncate" lang={current?.code}>{current?.label ?? locale.toUpperCase()}</span>
        <ChevronDownIcon className={cn('h-4 w-4 shrink-0 transition-transform duration-snap', open && 'rotate-180')} aria-hidden="true" />
      </button>

      {open && (
        <div
          className="absolute end-0 top-full z-[70] mt-2 w-64 max-w-[calc(100vw-2.5rem)] overflow-hidden rounded-card-sm border border-line bg-background text-foreground shadow-[0_24px_48px_-24px_rgba(17,17,17,0.35)] animate-pop-in"
          onKeyDown={onListKeyDown}
        >
          {searchable && (
            <div className="relative border-b border-line p-2">
              <SearchIcon className="pointer-events-none absolute start-5 top-1/2 h-4 w-4 -translate-y-1/2 text-secondary" aria-hidden="true" />
              <input
                ref={searchRef}
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    focusOption(0);
                  }
                }}
                aria-label={t('action.search')}
                placeholder={t('action.search')}
                className="min-h-10 w-full rounded-control border border-line bg-surface-field ps-9 pe-3 text-sm outline-none focus:border-line-strong focus:bg-background"
              />
            </div>
          )}
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            aria-labelledby={triggerId}
            className="max-h-72 overflow-y-auto p-1.5"
          >
            {visible.length === 0 && <li className="px-3 py-2 text-sm text-secondary">{t('state.emptyTitle')}</li>}
            {visible.map((item) => {
              const selected = item.code === locale;
              return (
                <li
                  key={item.code}
                  role="option"
                  aria-selected={selected}
                  data-value={item.code}
                  tabIndex={-1}
                  lang={item.code}
                  onClick={() => select(item.code)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      select(item.code);
                    }
                  }}
                  className={cn(
                    'flex cursor-pointer items-center gap-3 rounded-control px-3 py-2.5 text-sm outline-none transition-colors duration-snap focus:bg-surface fine:hover:bg-surface',
                    selected && 'font-medium',
                  )}
                >
                  <span className="min-w-0 flex-1 truncate">{item.label}</span>
                  <span className="micro shrink-0 text-secondary">{item.code}</span>
                  {selected && <CheckIcon className="h-4 w-4 shrink-0" aria-hidden="true" />}
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}
