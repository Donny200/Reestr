import { useI18n } from '../../contexts/i18n';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { FunctionStatusBadge } from '../ui/Badge';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { functionText, hasFunctionTranslation } from '../../utils/functionLocalization';
import type { AdminFunction } from '../../types/adminFunctions';

export function FunctionPreview({ record }: { record: AdminFunction }) {
  const { t, locale, available } = useI18n();
  const languageLabel = available.find((language) => language.code === locale)?.label ?? locale.toUpperCase();
  const description = functionText(record, 'description', locale);

  return (
    <Card>
      <CardHeader title={t('fnAdmin.preview')} description={languageLabel} />
      <CardBody className="space-y-3">
        <article lang={locale} className="rounded-card-sm bg-surface p-4">
          <div className="flex flex-wrap items-center gap-2">
            <FunctionStatusBadge status={record.status} />
            {record.category && <span className="text-xs font-medium text-secondary">{record.category}</span>}
          </div>
          <h3 className="mt-3 text-lg font-semibold leading-snug text-foreground wrap-anywhere">{functionText(record, 'name', locale)}</h3>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-secondary wrap-anywhere">{description || '—'}</p>
        </article>
        {!hasFunctionTranslation(record, locale) && <InlineAlert tone="warning">{t('fnAdmin.missingTranslation')}</InlineAlert>}
      </CardBody>
    </Card>
  );
}
