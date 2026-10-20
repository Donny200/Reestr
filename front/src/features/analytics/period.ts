import { addDays, formatCalendarDate } from '../../utils/format';

export const REPORTING_TIME_ZONE = 'Asia/Tashkent';
export const MAX_PERIOD_DAYS = 366;
export const PERIOD_PRESETS = ['7', '30', '90', '365'] as const;
export const DEFAULT_PRESET = '30';

export type PeriodPreset = (typeof PERIOD_PRESETS)[number] | 'custom';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function isIsoDate(value: string | null | undefined): value is string {
  return Boolean(value && ISO_DATE.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`)));
}

export function reportingToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: REPORTING_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

export function periodDays(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1;
}

export function presetRange(preset: Exclude<PeriodPreset, 'custom'>, end = reportingToday()): { from: string; to: string } {
  return { from: addDays(end, -(Number(preset) - 1)), to: end };
}

export function parsePreset(value: string | null): PeriodPreset {
  return value === 'custom' || PERIOD_PRESETS.includes(value as (typeof PERIOD_PRESETS)[number]) ? (value as PeriodPreset) : DEFAULT_PRESET;
}

export function rangeError(from: string, to: string): 'order' | 'length' | null {
  if (from > to) return 'order';
  if (periodDays(from, to) > MAX_PERIOD_DAYS) return 'length';
  return null;
}

export function formatDay(date: string, locale: string, options: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' }): string {
  return formatCalendarDate(date, locale, options);
}

export function formatPeriod(from: string, to: string, locale: string): string {
  return from === to ? formatDay(from, locale) : `${formatDay(from, locale)} – ${formatDay(to, locale)}`;
}
