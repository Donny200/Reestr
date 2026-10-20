import { BookmarkCheckIcon, BookmarkPlusIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import { useSavedServices, type SaveTarget } from './useSavedServices';

interface SaveServiceButtonProps {
  service: SaveTarget;
  compact?: boolean;
  className?: string;
}

export function SaveServiceButton({ service, compact = false, className }: SaveServiceButtonProps) {
  const { t } = useI18n();
  const { isSaved, save, remove, full } = useSavedServices();
  const saved = isSaved(service.id);
  const label = saved ? t('saved.remove', 'Remove from saved') : t('saved.save', 'Save');

  const toggle = () => {
    if (saved) {
      remove(service.id);
      toast.success(t('saved.removed', 'Removed from saved services'));
      return;
    }
    if (full) {
      toast.error(t('saved.full', 'You can save up to 100 services. Remove some to add more.'));
      return;
    }
    if (save(service)) {
      toast.success(t('saved.added', 'Saved in this browser'));
    } else {
      toast.warning(t('saved.sessionOnly', 'Saved for this visit only: this browser blocks local storage.'));
    }
  };

  const Icon = saved ? BookmarkCheckIcon : BookmarkPlusIcon;

  if (compact) {
    return (
      <Button
        variant={saved ? 'dark' : 'outline'}
        size="iconSm"
        aria-pressed={saved}
        aria-label={`${label}: ${service.name}`}
        title={label}
        onClick={toggle}
        className={cn('print:hidden', className)}
      >
        <Icon aria-hidden="true" />
      </Button>
    );
  }

  return (
    <Button variant={saved ? 'dark' : 'outline'} size="sm" aria-pressed={saved} icon={<Icon />} onClick={toggle} className={cn('print:hidden', className)}>
      {saved ? t('saved.saved', 'Saved') : t('saved.save', 'Save')}
    </Button>
  );
}
