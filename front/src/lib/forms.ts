import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { errorMessage, fieldErrorsOf, type Translate } from '../utils/errors';

export const withoutPrefix = (...prefixes: string[]) => (field: string) => {
  const prefix = prefixes.find((candidate) => field.startsWith(`${candidate}.`));
  return prefix ? field.slice(prefix.length + 1) : field;
};

export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  onFormError: (message: string) => void,
  t?: Translate,
  fieldName: (field: string) => string = (field) => field,
): void {
  const fields = fieldErrorsOf(error);
  const names = Object.keys(fields);
  if (names.length === 0) {
    onFormError(errorMessage(error, t));
    return;
  }
  names.forEach((name) => setError(fieldName(name) as Path<T>, { type: 'server', message: fields[name] }));
}
