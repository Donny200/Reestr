import { useState } from 'react';
import { DownloadIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../contexts/i18n';
import type { DownloadResult } from '../../services/http';
import type { CsvDelimiter } from '../../types/analytics';
import { errorMessage } from '../../utils/errors';
import { preferredCsvDelimiter } from './csv';

interface ExportButtonProps {
  label: string;
  run: (delimiter: CsvDelimiter) => Promise<DownloadResult>;
  disabled?: boolean;
}

export function ExportButton({ label, run, disabled = false }: ExportButtonProps) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);

  const download = async () => {
    setBusy(true);
    try {
      const result = await run(preferredCsvDelimiter());
      if (result.truncated) {
        toast.warning(t('export.truncated', 'The export contains the first 10,000 rows. Narrow the filters to export the rest.'));
      } else {
        toast.success(`${t('export.ready', 'Downloaded:')} ${result.filename}`);
      }
    } catch (error) {
      toast.error(errorMessage(error, t));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button variant="outline" size="sm" icon={<DownloadIcon />} loading={busy} disabled={disabled} onClick={() => void download()}>
      {label}
    </Button>
  );
}
