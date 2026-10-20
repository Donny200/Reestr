import type { CsvDelimiter } from '../../types/analytics';

export function preferredCsvDelimiter(): CsvDelimiter {
  try {
    const locale = typeof navigator === 'undefined' ? 'en' : navigator.language;
    const decimal = new Intl.NumberFormat(locale).formatToParts(1.5).find((part) => part.type === 'decimal')?.value;
    return decimal === ',' ? 'SEMICOLON' : 'COMMA';
  } catch {
    return 'COMMA';
  }
}
