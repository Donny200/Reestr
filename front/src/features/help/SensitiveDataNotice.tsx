import { ShieldAlertIcon } from 'lucide-react';
import { useI18n } from '../../contexts/i18n';

export function SensitiveDataNotice() {
  const { t } = useI18n();
  return (
    <p className="flex items-start gap-2.5 rounded-control bg-status-pending-bg px-4 py-3 text-sm leading-6 text-status-pending">
      <ShieldAlertIcon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <span>
        {t('support.sensitiveNotice', 'Do not send passwords, personal identification numbers (PINFL), passport, bank card or other payment details, or copies of documents. They are not needed to answer you.')}
      </span>
    </p>
  );
}
