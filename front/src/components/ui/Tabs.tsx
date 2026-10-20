import { useRef, type KeyboardEvent } from 'react';
import { cn } from '../../lib/cn';

export interface TabItem {
  id: string;
  label: string;
  complete?: boolean;
  incompleteLabel?: string;
  lang?: string;
}

interface TabsProps {
  items: TabItem[];
  value: string;
  onChange: (id: string) => void;
  label: string;
  disabled?: boolean;
  className?: string;
}

export function Tabs({ items, value, onChange, label, disabled = false, className }: TabsProps) {
  const listRef = useRef<HTMLDivElement>(null);

  const focusTab = (index: number) => {
    const tabs = listRef.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    if (!tabs || tabs.length === 0) return;
    const next = (index + tabs.length) % tabs.length;
    tabs[next].focus();
    onChange(items[next].id);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const rtl = document.documentElement.dir === 'rtl';
    const forward = rtl ? 'ArrowLeft' : 'ArrowRight';
    const backward = rtl ? 'ArrowRight' : 'ArrowLeft';
    if (event.key === forward) focusTab(index + 1);
    else if (event.key === backward) focusTab(index - 1);
    else if (event.key === 'Home') focusTab(0);
    else if (event.key === 'End') focusTab(items.length - 1);
    else return;
    event.preventDefault();
  };

  return (
    <div
      ref={listRef}
      role="tablist"
      aria-label={label}
      className={cn('no-scrollbar relative flex max-w-full gap-1.5 overflow-x-auto rounded-pill bg-surface p-1', className)}
    >
      {items.map((item, index) => {
        const active = item.id === value;
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            lang={item.lang}
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            disabled={disabled}
            onClick={() => onChange(item.id)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              'inline-flex min-h-9 shrink-0 items-center gap-2 rounded-pill px-4 text-sm font-medium transition-colors duration-snap ease-snap disabled:cursor-not-allowed disabled:text-secondary',
              active ? 'bg-ink text-ink-fg' : 'text-secondary fine:hover:bg-surface-2 fine:hover:text-foreground',
            )}
          >
            {item.complete !== undefined && (
              <span
                className={cn('h-1.5 w-1.5 shrink-0 rounded-pill', item.complete ? 'bg-status-published' : active ? 'bg-ink-fg/50' : 'bg-subtle')}
                aria-hidden="true"
              />
            )}
            {item.label}
            {item.complete === false && item.incompleteLabel && <span className="sr-only"> ({item.incompleteLabel})</span>}
          </button>
        );
      })}
    </div>
  );
}
