import type { Translate } from './errors';

const formatters = new Map<string, Intl.DateTimeFormat>();

/** Formatters are costly to build and tables format every cell, so each locale and option set is built once. */
function dateTimeFormat(locale: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = `${locale}|${JSON.stringify(options)}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, options);
    formatters.set(key, formatter);
  }
  return formatter;
}

const NUMERIC_DATE: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit', year: 'numeric' };

export function formatDate(value: string | null | undefined, locale = 'uz-UZ'): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  try {
    return dateTimeFormat(locale, NUMERIC_DATE).format(date);
  } catch {
    return dateTimeFormat('uz-UZ', NUMERIC_DATE).format(date);
  }
}

export function formatDateTime(value: string, locale: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  try {
    return dateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
  } catch {
    return date.toLocaleString();
  }
}

/** A calendar date ("2026-10-19") as readable text, without shifting it through the browser's time zone. */
export function formatCalendarDate(date: string, locale: string, options: Intl.DateTimeFormatOptions): string {
  const [year, month, day] = date.split('-').map(Number);
  try {
    return dateTimeFormat(locale, { ...options, timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, day, 12)));
  } catch {
    return date;
  }
}

/** Calendar date ("2026-10-19") moved by whole days. */
export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function roleLabel(role: string, t?: Translate): string {
  switch (role) {
    case 'ROLE_SUPER_ADMIN':
      return t?.('role.superAdmin', 'Super admin') ?? 'Super admin';
    case 'ROLE_ORG_ADMIN':
      return t?.('role.orgAdmin', 'Tashkilot admini') ?? 'Tashkilot admini';
    case 'ROLE_MODERATOR':
      return t?.('role.moderator', 'Moderator') ?? 'Moderator';
    case 'ROLE_USER':
      return t?.('role.user', 'Foydalanuvchi') ?? 'Foydalanuvchi';
    default:
      return role;
  }
}

export function fullName(user: {firstName: string;lastName: string;}): string {
  return `${user.lastName} ${user.firstName}`.trim();
}

export function initials(user: {firstName: string;lastName: string;}): string {
  return `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase();
}

export function organizationInitials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean);
  return words.slice(0, 2).map((word) => word.charAt(0)).join('').toUpperCase() || name.charAt(0).toUpperCase();
}

export function truncate(value: string | null | undefined, length = 120): string {
  if (!value) return '—';
  return value.length > length ? `${value.slice(0, length).trimEnd()}…` : value;
}

export function requirementLines(requirements: string | null | undefined): string[] {
  if (!requirements) return [];
  return requirements.
  split('\n').
  map((line) => line.replace(/^\s*\d+[.)]\s*/, '').trim()).
  filter(Boolean);
}
