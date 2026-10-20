import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRightIcon } from 'lucide-react';
import { cn } from '../../lib/cn';

interface ListRowProps {
  to: string;
  index: number;
  title: string;
  secondary?: ReactNode;
  meta?: ReactNode;
  lang?: string;
  className?: string;
  action?: ReactNode;
}

export function ListRow({ to, index, title, secondary, meta, lang, className, action }: ListRowProps) {
  return (
    <li className={cn('border-t border-line', action && 'flex items-center gap-2 sm:gap-3', className)}>
      <Link
        to={to}
        className="group grid min-w-0 flex-1 grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-4 py-5 ps-0 pe-0 transition-[background-color,padding] duration-snap ease-snap fine:hover:bg-surface fine:hover:ps-6 fine:hover:pe-5 sm:gap-6 sm:py-6 sm:ps-6 sm:pe-6 sm:fine:hover:ps-8"
      >
        <span className="micro w-8 pt-1 text-secondary tabular-nums sm:w-10" aria-hidden="true">
          {String(index).padStart(2, '0')}
        </span>
        <span className="min-w-0">
          <span lang={lang} className="block wrap-anywhere text-xl font-medium leading-tight text-foreground sm:text-2xl lg:text-[2.25rem] lg:leading-[1.1]">
            {title}
          </span>
          {secondary && <span lang={lang} className="mt-2 block wrap-anywhere text-sm leading-6 text-secondary">{secondary}</span>}
          {meta && <span className="mt-3 flex flex-wrap items-center gap-2">{meta}</span>}
        </span>
        <span
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-pill bg-ink text-ink-fg transition-transform duration-snap ease-snap motion-safe:fine:group-hover:translate-x-[5px] motion-safe:rtl:fine:group-hover:-translate-x-[5px]"
          aria-hidden="true"
        >
          <ArrowUpRightIcon className="h-5 w-5 rtl:-scale-x-100" strokeWidth={2} />
        </span>
      </Link>
      {action && <div className="shrink-0 pe-1 sm:pe-2">{action}</div>}
    </li>
  );
}
