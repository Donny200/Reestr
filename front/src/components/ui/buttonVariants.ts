import { cva } from 'class-variance-authority';

export const buttonVariants = cva(
  'group relative inline-flex min-h-10 select-none items-center justify-center gap-2 whitespace-nowrap rounded-pill border border-transparent text-sm font-medium transition-[transform,background-color,color,border-color] duration-snap ease-snap motion-safe:fine:hover:scale-[1.04] motion-safe:active:scale-[0.98] disabled:cursor-not-allowed disabled:border-transparent disabled:bg-surface-2 disabled:text-secondary disabled:hover:scale-100 [&_svg]:h-4 [&_svg]:w-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        dark: 'bg-ink text-ink-fg',
        light: 'bg-surface text-foreground fine:hover:bg-surface-2',
        outline: 'border-line bg-transparent text-foreground fine:hover:bg-surface',
        ghost: 'bg-transparent text-secondary fine:hover:bg-surface fine:hover:text-foreground',
        danger: 'bg-danger-solid text-danger-fg',
        link: 'min-h-0 rounded-sm px-0 text-accent-text underline-offset-4 hover:underline motion-safe:fine:hover:scale-100',
      },
      size: {
        sm: 'min-h-9 px-4 py-1.5',
        md: 'px-7 py-3.5',
        lg: 'px-8 py-4 text-base',
        icon: 'h-10 w-10 p-0',
        iconSm: 'h-9 w-9 p-0',
      },
      arrow: {
        true: 'ps-6 pe-1.5 py-1.5',
      },
    },
    defaultVariants: { variant: 'dark', size: 'md' },
  },
);

export const arrowBadgeVariants = cva(
  'flex h-9 w-9 shrink-0 items-center justify-center rounded-pill transition-transform duration-snap ease-snap motion-safe:fine:group-hover:translate-x-[3px] motion-safe:rtl:fine:group-hover:-translate-x-[3px] [&_svg]:rtl:-scale-x-100',
  {
    variants: {
      variant: {
        dark: 'bg-background text-ink',
        light: 'bg-ink text-ink-fg',
        outline: 'bg-ink text-ink-fg',
        ghost: 'bg-ink text-ink-fg',
        danger: 'bg-background text-danger-solid',
        link: 'bg-ink text-ink-fg',
      },
    },
    defaultVariants: { variant: 'dark' },
  },
);
