import { PrinterIcon } from 'lucide-react';
import { Button } from '../ui/Button';
import { useI18n } from '../../contexts/i18n';

export function PrintButton({ label }: { label?: string }) {
  const { t } = useI18n();
  return (
    <Button variant="outline" size="sm" icon={<PrinterIcon />} onClick={() => window.print()} className="print:hidden">
      {label ?? t('print.action', 'Print')}
    </Button>
  );
}
