import type { Permission } from '../../types/api';
import type { Translate } from '../../utils/errors';

export type PermissionGroup = [string, Permission[]];

/** identity-service stores permission groups by English name; these keys translate them. */
const CATEGORY_KEYS: Record<string, string> = {
  'Organization Management': 'permission.category.organizations',
  'Org Admin Management': 'permission.category.orgAdmins',
  'Moderator Management': 'permission.category.moderators',
  'User Management': 'permission.category.users',
  'Language Management': 'permission.category.languages',
  'Function Management': 'permission.category.functions',
  'Translation Management': 'permission.category.translations',
  'Role & Permission Management': 'permission.category.roles',
  Reports: 'permission.category.reports',
  Analytics: 'permission.category.analytics',
  'Visitor questions': 'permission.category.visitorQuestions',
  Appointments: 'permission.category.appointments',
};

/** Translated description of a permission; its code stays visible next to it as the identifier. */
export function permissionLabel(permission: Permission, t: Translate): string {
  return t(`permission.${permission.code}`, permission.name);
}

export function permissionCategoryLabel(category: string, t: Translate): string {
  const key = CATEGORY_KEYS[category];
  return key ? t(key, category) : category;
}

export function groupPermissions(permissions: Permission[], fallbackCategory: string, t?: Translate): PermissionGroup[] {
  const groups = new Map<string, Permission[]>();
  permissions.forEach((permission) => {
    const category = permission.category ? (t ? permissionCategoryLabel(permission.category, t) : permission.category) : fallbackCategory;
    groups.set(category, [...(groups.get(category) ?? []), permission]);
  });
  return Array.from(groups.entries());
}
