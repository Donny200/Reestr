import { cn } from '../../lib/cn';
import { initials } from '../../utils/format';

interface AvatarProps {
  user: { firstName: string; lastName: string };
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const sizes = { sm: 'h-8 w-8 text-[11px]', md: 'h-10 w-10 text-xs', lg: 'h-14 w-14 text-base' };

export function Avatar({ user, size = 'md', className }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={cn('inline-flex shrink-0 items-center justify-center rounded-pill bg-ink font-semibold tracking-wide text-ink-fg', sizes[size], className)}
    >
      {initials(user)}
    </span>
  );
}
