import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRightIcon } from 'lucide-react';
import { Eyebrow } from '../ui/Eyebrow';

export interface Crumb {
  label: string;
  to?: string;
}

interface PageHeaderProps {
  title: string;
  description?: string;
  eyebrow?: string;
  actions?: ReactNode;
  badge?: ReactNode;
  breadcrumbs?: Crumb[];
}

export function PageHeader({ title, description, eyebrow, actions, badge, breadcrumbs }: PageHeaderProps) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:mb-8 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav aria-label={breadcrumbs[0]?.label} className="mb-3">
            <ol className="flex flex-wrap items-center gap-1 text-sm text-secondary">
              {breadcrumbs.map((crumb, index) => (
                <li key={`${crumb.label}-${index}`} className="flex min-w-0 items-center gap-1">
                  {index > 0 && <ChevronRightIcon className="h-3.5 w-3.5 shrink-0 rtl:-scale-x-100" aria-hidden="true" />}
                  {crumb.to ? (
                    <Link to={crumb.to} className="rounded-sm transition-colors fine:hover:text-foreground">{crumb.label}</Link>
                  ) : (
                    <span aria-current="page" className="truncate text-foreground">{crumb.label}</span>
                  )}
                </li>
              ))}
            </ol>
          </nav>
        )}
        {eyebrow && <Eyebrow className="mb-2">{eyebrow}</Eyebrow>}
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-semibold text-foreground wrap-anywhere sm:text-4xl">{title}</h1>
          {badge}
        </div>
        {description && <p className="mt-2 max-w-2xl text-base leading-6 text-secondary wrap-anywhere">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2 [&>*]:flex-1 sm:[&>*]:flex-none">{actions}</div>}
    </div>
  );
}
