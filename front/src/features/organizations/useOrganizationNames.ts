import { useMemo } from 'react';
import { useI18n } from '../../contexts/i18n';
import type { PublicOrganization } from '../../types/api';
import { localizedText } from '../../utils/translations';

/** Organization names in the current language, by id. */
export function useOrganizationNames(organizations: PublicOrganization[] | undefined): Map<number, string> {
  const { locale } = useI18n();
  return useMemo(
    () => new Map((organizations ?? []).map((item) => [item.id, localizedText(item.name, item.nameTranslations, locale)])),
    [organizations, locale],
  );
}
