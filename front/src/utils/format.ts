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

const unitFormats = new Map<string, Intl.NumberFormat>();

export function formatPercent(value: number, locale: string): string {
  try {
    return new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 1 }).format(value);
  } catch {
    return `${Math.round(value * 1000) / 10}%`;
  }
}

/** "30 min", "2 h", "1 d" in the interface language (CLDR unit names). */
export function formatDuration(value: number, unit: 'minute' | 'hour' | 'day', locale: string): string {
  const key = `${locale}|${unit}`;
  let format = unitFormats.get(key);
  if (!format) {
    try {
      format = new Intl.NumberFormat(locale, { style: 'unit', unit, unitDisplay: 'short' });
    } catch {
      format = new Intl.NumberFormat('en', { style: 'unit', unit, unitDisplay: 'short' });
    }
    unitFormats.set(key, format);
  }
  return format.format(value);
}

export function roleLabel(role: string, t?: Translate): string {
  switch (role) {
    case 'ROLE_SUPER_ADMIN':
      return t?.('role.superAdmin', 'Super admin') ?? 'Super admin';
    case 'ROLE_ORG_ADMIN':
      return t?.('role.orgAdmin', 'Org admin') ?? 'Org admin';
    case 'ROLE_MODERATOR':
      return t?.('role.moderator', 'Moderator') ?? 'Moderator';
    case 'ROLE_USER':
      return t?.('role.user', 'User') ?? 'User';
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
