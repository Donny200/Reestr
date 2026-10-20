import { APPOINTMENT_VIEW_PERMISSIONS } from '../../features/appointments/access';
import { HELP_VIEW_PERMISSION } from '../../features/help/helpOptions';
import { REPORT_VIEW_PERMISSIONS } from '../../features/reports/reportOptions';
import { FUNCTION_PERMISSIONS } from '../../utils/functionPermissions';
import type { RoleName } from '../../types/api';

export interface AdminNavItem {
  to: string;
  labelKey: string;
  icon: 'dashboard' | 'building' | 'shield' | 'users' | 'key' | 'globe' | 'legacy' | 'lock' | 'functions' | 'reports' | 'analytics' | 'questions' | 'calendar';
  roles: RoleName[];
  anyPermissions?: readonly string[];
  end?: boolean;
  dividerBefore?: boolean;
}

export const adminNav: AdminNavItem[] = [
{ to: '/admin/functions', labelKey: 'nav.functions', icon: 'functions', roles: [], anyPermissions: FUNCTION_PERMISSIONS },
{ to: '/admin/reports', labelKey: 'nav.reports', icon: 'reports', roles: [], anyPermissions: REPORT_VIEW_PERMISSIONS },
{ to: '/admin/questions', labelKey: 'nav.supportInbox', icon: 'questions', roles: [], anyPermissions: [HELP_VIEW_PERMISSION] },
{ to: '/admin/appointments', labelKey: 'nav.appointments', icon: 'calendar', roles: [], anyPermissions: APPOINTMENT_VIEW_PERMISSIONS },
{ to: '/admin/organization-dashboard', labelKey: 'nav.orgDashboard', icon: 'analytics', roles: [], anyPermissions: ['ORG_ANALYTICS_VIEW'] },
{
  to: '/admin',
  labelKey: 'nav.dashboard',
  icon: 'dashboard',
  roles: ['ROLE_SUPER_ADMIN', 'ROLE_ORG_ADMIN', 'ROLE_MODERATOR'],
  end: true
},
{
  to: '/admin/organizations',
  labelKey: 'nav.organizations',
  icon: 'building',
  roles: ['ROLE_SUPER_ADMIN', 'ROLE_ORG_ADMIN', 'ROLE_MODERATOR']
},
{
  to: '/admin/org-admins',
  labelKey: 'nav.orgAdmins',
  icon: 'shield',
  roles: ['ROLE_SUPER_ADMIN'],
  dividerBefore: true
},
{
  to: '/admin/moderators',
  labelKey: 'nav.moderators',
  icon: 'users',
  roles: ['ROLE_SUPER_ADMIN', 'ROLE_ORG_ADMIN']
},
{ to: '/admin/roles', labelKey: 'nav.roles', icon: 'key', roles: ['ROLE_SUPER_ADMIN'] },
{ to: '/admin/languages', labelKey: 'nav.languages', icon: 'globe', roles: ['ROLE_SUPER_ADMIN'] },
{ to: '/admin/users', labelKey: 'nav.legacyUsers', icon: 'legacy', roles: ['ROLE_SUPER_ADMIN'] },
{
  to: '/settings/security',
  labelKey: 'nav.security',
  icon: 'lock',
  roles: ['ROLE_SUPER_ADMIN', 'ROLE_ORG_ADMIN', 'ROLE_MODERATOR'],
  dividerBefore: true
}];


export function navForRole(role: RoleName | undefined, permissions: string[] = []): AdminNavItem[] {
  if (!role) return [];
  return adminNav.filter((item) => item.anyPermissions ? item.anyPermissions.some((permission) => permissions.includes(permission)) : item.roles.includes(role));
}
