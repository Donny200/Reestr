import { ListRow } from '../ui/ListRow';
import { Badge } from '../ui/Badge';
import type { CatalogFunction } from '../../types/api';
import { truncate } from '../../utils/format';
import { useI18n } from '../../contexts/i18n';
import { localizedText } from '../../utils/translations';
import { SaveServiceButton } from '../../features/saved/SaveServiceButton';
import { formatDate } from '../../utils/format';
import { useCategoryNames } from '../../features/functions/useCategoryNames';

interface FunctionListRowProps {
  item: CatalogFunction;
  index: number;
  organizationName: string | undefined;
  /** Short explanation of why the service is listed (search results). */
  reason?: string;
}

export function FunctionListRow({ item, index, organizationName, reason }: FunctionListRowProps) {
  const { t, locale } = useI18n();
  const name = localizedText(item.name, item.nameTranslations, locale);
  const description = localizedText(item.description, item.descriptionTranslations, locale);
  const category = useCategoryNames()(item.categoryId, item.category);

  return (
    <ListRow
      to={`/functions/${item.id}`}
      index={index}
      title={name}
      lang={locale}
      secondary={description ? truncate(description, 160) : undefined}
      meta={
        <>
          <Badge size="sm">{organizationName ?? t('fn.organizationUnknown', 'Organization unknown')}</Badge>
          {category && <Badge size="sm" tone="accent">{category}</Badge>}
          {reason && <span className="text-xs text-secondary">{reason}</span>}
          {item.lastUpdatedAt && (
            <span className="text-xs tabular-nums text-secondary">{t('history.updatedOn', 'Updated')} {formatDate(item.lastUpdatedAt, locale)}</span>
          )}
        </>
      }
      action={
        <SaveServiceButton compact service={{ id: item.id, name }} />
      }
    />
  );
}
