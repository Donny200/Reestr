import type { ComponentType } from 'react';
import { CheckIcon, MonitorIcon, MoonIcon, SunIcon } from 'lucide-react';
import { useTheme, type Theme } from '../../contexts/theme';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import { Button } from './Button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from './DropdownMenu';

const options: { value: Theme; icon: ComponentType<{ className?: string }>; key: string; fallback: string }[] = [
  { value: 'light', icon: SunIcon, key: 'theme.light', fallback: 'Light' },
  { value: 'dark', icon: MoonIcon, key: 'theme.dark', fallback: 'Dark' },
  { value: 'system', icon: MonitorIcon, key: 'theme.system', fallback: 'System' },
];

export function ThemeToggle({ onInk = false }: { onInk?: boolean }) {
  const { theme, resolved, setTheme } = useTheme();
  const { t } = useI18n();
  const CurrentIcon = resolved === 'dark' ? MoonIcon : SunIcon;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className={cn(onInk && 'text-ink-fg fine:hover:bg-ink-hover fine:hover:text-ink-fg')} aria-label={t('theme.toggle', 'Change theme')}>
          <CurrentIcon aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {options.map((option) => (
          <DropdownMenuItem key={option.value} onSelect={() => setTheme(option.value)}>
            <option.icon />
            {t(option.key, option.fallback)}
            {theme === option.value && <CheckIcon className="ms-auto" aria-hidden="true" />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
