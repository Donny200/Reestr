import type { CSSProperties, ElementType, ReactNode } from 'react';
import { cn } from '../../lib/cn';
import { useReveal } from '../../hooks/useReveal';

interface RevealProps {
  as?: ElementType;
  index?: number;
  className?: string;
  children: ReactNode;
  id?: string;
}

const STAGGER_MS = 80;
const MAX_DELAY_MS = 400;

export function Reveal({ as: Tag = 'div', index = 0, className, children, id }: RevealProps) {
  const { ref, visible } = useReveal<HTMLElement>();
  const style = { '--reveal-delay': `${Math.min(index * STAGGER_MS, MAX_DELAY_MS)}ms` } as CSSProperties;
  return (
    <Tag ref={ref} id={id} style={style} className={cn('reveal', visible && 'is-visible', className)}>
      {children}
    </Tag>
  );
}
