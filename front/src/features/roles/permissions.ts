import type { Permission } from '../../types/api';

export type PermissionGroup = [string, Permission[]];

export function groupPermissions(permissions: Permission[], fallbackCategory: string): PermissionGroup[] {
  const groups = new Map<string, Permission[]>();
  permissions.forEach((permission) => {
    const category = permission.category ?? fallbackCategory;
    groups.set(category, [...(groups.get(category) ?? []), permission]);
  });
  return Array.from(groups.entries());
}
