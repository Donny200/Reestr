import { ApiError, type FieldIssue } from '../services/http';

/** Any lookup function; the i18n `t` also carries the locale of its dictionary. */
export type Translate = ((key: string, fallback?: string) => string) & { readonly locale?: string };

const fallbackTranslate: Translate = (key, fallback) => fallback ?? key;

/**
 * Message for an API failure. Services answer in English, so their own text is shown only while the interface is in
 * English; other languages get the translated message for the status instead of an English sentence.
 */
export function errorMessage(error: unknown, t: Translate = fallbackTranslate): string {
  const english = !t.locale || t.locale.toLowerCase().split(/[-_]/)[0] === 'en';
  if (error instanceof ApiError) {
    const server = english ? error.message : '';
    switch (error.status) {
      case 400:
        return server || t('error.badRequest', 'The submitted data is invalid');
      case 401:
        return t('error.sessionExpired', 'Your session has expired, sign in again');
      case 403:
        return t('state.forbidden', 'You do not have permission for this action');
      case 404:
        return server || t('error.notFound', 'Not found');
      case 409:
        return server || t('error.conflict', 'The operation conflicts with the current state');
      case 429:
        return t('error.rateLimited', 'Too many requests in a short time. Please wait a moment and try again.');
      case 0:
        return t('error.network', 'Could not reach the server');
      default:
        return server || t('error.unexpected', 'Something went wrong');
    }
  }
  if (error instanceof Error && english) return error.message;
  return t('error.unexpected', 'Something went wrong');
}

export function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null;
}

/** Field messages from the server (English); in other languages each field gets the translated generic message. */
export function fieldErrorsOf(error: unknown, t?: Translate): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  const english = !t?.locale || t.locale.toLowerCase().split(/[-_]/)[0] === 'en';
  return error.fieldErrors.reduce<Record<string, string>>((acc, issue: FieldIssue) => {
    acc[issue.field] = english || !t ? issue.message : t('validation.invalidField', 'Check this field');
    return acc;
  }, {});
}
