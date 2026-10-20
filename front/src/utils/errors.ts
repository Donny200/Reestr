import { ApiError, type FieldIssue } from '../services/http';

export type Translate = (key: string, fallback?: string) => string;

const fallbackTranslate: Translate = (key, fallback) => fallback ?? key;

export function errorMessage(error: unknown, t: Translate = fallbackTranslate): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 400:
        return error.message || t('error.badRequest', 'The submitted data is invalid');
      case 401:
        return t('error.sessionExpired', 'Your session has expired, sign in again');
      case 403:
        return t('state.forbidden', 'You do not have permission for this action');
      case 404:
        return error.message || t('error.notFound', 'Not found');
      case 409:
        return error.message || t('error.conflict', 'The operation conflicts with the current state');
      case 0:
        return t('error.network', 'Could not reach the server');
      default:
        return error.message || t('error.unexpected', 'Something went wrong');
    }
  }
  if (error instanceof Error) return error.message;
  return t('error.unexpected', 'Something went wrong');
}

export function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null;
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  return error.fieldErrors.reduce<Record<string, string>>((acc, issue: FieldIssue) => {
    acc[issue.field] = issue.message;
    return acc;
  }, {});
}
