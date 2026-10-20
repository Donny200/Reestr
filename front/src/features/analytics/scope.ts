import { useMemo } from 'react';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { useFunctionOptions } from '../functions/queries';
import { localizedText } from '../../utils/translations';
import type { SelectOption } from '../../components/ui/Select';

export function parseId(value: string | null): number | undefined {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

export function useScopeOptions() {
  const { t, locale } = useI18n();
  const { user, isSuperAdmin, hasPermission } = useAuth();
  const options = useFunctionOptions();
  const global = isSuperAdmin || hasPermission('FUNCTIONS_MANAGE_ANY_ORGANIZATION');
  const showOrganizations = global || (user?.organizationIds.length ?? 0) > 1;

  const organizations = useMemo<SelectOption[]>(
    () => [
      { value: '', label: global ? t('analytics.allOrganizations', 'All organizations') : t('analytics.allMyOrganizations', 'All my organizations') },
      ...(options.data?.organizations ?? []).map((organization) => ({ value: String(organization.id), label: organization.name })),
    ],
    [global, options.data, t],
  );

  const categories = useMemo<SelectOption[]>(
    () => [
      { value: '', label: t('analytics.allCategories', 'All categories') },
      ...(options.data?.categories ?? []).map((category) => ({
        value: String(category.id),
        label: localizedText(category.name, category.nameTranslations, locale),
      })),
    ],
    [options.data, t, locale],
  );

  const organizationName = (id: number | null | undefined) =>
    options.data?.organizations.find((organization) => organization.id === id)?.name ?? (id ? `#${id}` : '—');

  return { global, showOrganizations, organizations, categories, organizationName, loading: options.isPending };
}
