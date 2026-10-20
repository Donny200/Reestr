import { FileDownIcon } from 'lucide-react';
import { API_BASE_URL } from '../../config/env';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import { buttonVariants } from '../ui/buttonVariants';

function guideUrl(serviceId: number, language: string): string {
  return `${API_BASE_URL.replace(/\/$/, '')}/api/functions/${serviceId}/guide.pdf?lang=${encodeURIComponent(language)}`;
}

/** Public PDF guide of a published service in the current interface language; no sign-in needed. */
export function GuideDownloadLink({ serviceId }: { serviceId: number }) {
  const { t, locale } = useI18n();
  return (
    <a href={guideUrl(serviceId, locale)} download className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'print:hidden')}>
      <FileDownIcon aria-hidden="true" />
      {t('guide.download', 'Download guide (PDF)')}
    </a>
  );
}
