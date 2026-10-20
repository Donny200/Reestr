import type { ReactNode } from 'react';
import { Eyebrow } from '../ui/Eyebrow';

interface SectionHeadingProps {
  eyebrow: string;
  title: string;
  description?: string;
  aside?: ReactNode;
  onInk?: boolean;
}

export function SectionHeading({ eyebrow, title, description, aside, onInk = false }: SectionHeadingProps) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="max-w-2xl">
        <Eyebrow onInk={onInk}>{eyebrow}</Eyebrow>
        <h2 className="mt-3 text-4xl font-semibold wrap-anywhere sm:text-5xl">{title}</h2>
        {description && <p className={onInk ? 'mt-3 text-base leading-7 text-ink-secondary' : 'mt-3 text-base leading-7 text-secondary'}>{description}</p>}
      </div>
      {aside}
    </div>
  );
}
