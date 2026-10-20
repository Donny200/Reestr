import { ArrowLeftIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { buttonVariants } from '../ui/buttonVariants';
import { cn } from '../../lib/cn';

export function BackLink({ to, label }: { to: string; label: string }) {
  return (
    <Link to={to} className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'ps-3')}>
      <ArrowLeftIcon className="rtl:-scale-x-100" aria-hidden="true" />
      {label}
    </Link>
  );
}
