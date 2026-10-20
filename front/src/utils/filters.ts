import type { SelectOption } from '../components/ui/Select';
import type { Translate } from './errors';

export type StatusFilter = '' | 'active' | 'inactive';

export function statusFilterOptions(t: Translate): SelectOption[] {
  return [
    { value: '', label: t('status.all') },
    { value: 'active', label: t('status.active') },
    { value: 'inactive', label: t('status.inactive') },
  ];
}

export function matchesStatus(enabled: boolean, filter: StatusFilter): boolean {
  if (filter === 'active') return enabled;
  if (filter === 'inactive') return !enabled;
  return true;
}
