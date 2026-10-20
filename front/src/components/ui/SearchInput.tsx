import { useId } from 'react';
import { SearchIcon, XIcon } from 'lucide-react';
import { cn } from '../../lib/cn';
import { useI18n } from '../../contexts/i18n';
import { Input } from './Input';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder?: string;
  className?: string;
}

export function SearchInput({ value, onChange, label, placeholder, className }: SearchInputProps) {
  const id = useId();
  const { t } = useI18n();
  return (
    <div className={cn('relative', className)}>
      <label htmlFor={id} className="sr-only">{label}</label>
      <SearchIcon className="pointer-events-none absolute start-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-secondary" aria-hidden="true" />
      <Input
        id={id}
        type="search"
        value={value}
        placeholder={placeholder ?? label}
        onChange={(event) => onChange(event.target.value)}
        className="min-h-11 ps-10 pe-10 py-2 text-sm [&::-webkit-search-cancel-button]:hidden"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label={t('action.reset')}
          className="absolute end-1 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-pill text-secondary transition-colors fine:hover:bg-surface-2 fine:hover:text-foreground"
        >
          <XIcon className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
