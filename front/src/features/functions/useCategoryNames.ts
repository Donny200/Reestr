import { useCallback, useMemo } from 'react';
import { useI18n } from '../../contexts/i18n';
import { localizedText } from '../../utils/translations';
import { useFunctionCategories } from './queries';

/**
 * Category names in the interface language. Service responses carry only the source-language name, so the public
 * category list (cached for minutes and shared by every page) supplies the translations.
 */
export function useCategoryNames() {
  const { locale } = useI18n();
  const categories = useFunctionCategories();
  const byId = useMemo(
    () => new Map((categories.data ?? []).map((category) => [category.id, localizedText(category.name, category.nameTranslations, locale)])),
    [categories.data, locale],
  );
  return useCallback(
    (categoryId: number | null | undefined, sourceName: string | null | undefined) =>
      (categoryId != null ? byId.get(categoryId) : undefined) ?? sourceName ?? null,
    [byId],
  );
}
