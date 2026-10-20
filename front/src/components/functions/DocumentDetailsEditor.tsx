import { Controller, type UseFormReturn } from 'react-hook-form';
import { Field } from '../ui/Field';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { useI18n } from '../../contexts/i18n';
import { COPIES_MAX, NOTES_MAX, SOURCE_URL_MAX, VALIDITY_MAX } from '../../features/functions/schema';
import type { DocumentDetailValues, FunctionFormValues } from '../../types/adminFunctions';
import { DOCUMENT_FORMS, documentFormLabels, documentLines } from '../../utils/documentDetails';

const EMPTY: Omit<DocumentDetailValues, 'document'> = { form: '', copies: '', validity: '', notes: '', formUrl: '' };

/**
 * Optional structured details per line of "Required documents". Only what the organization actually knows is filled
 * in; lines without details are shown to visitors as "not provided by the organization". Details of a line that is
 * removed from the list are not saved.
 */
export function DocumentDetailsEditor({ form, disabled }: { form: UseFormReturn<FunctionFormValues>; disabled: boolean }) {
  const { t } = useI18n();
  const lines = documentLines(form.watch('requiredDocuments'));
  const errors = form.formState.errors.documentDetails;

  return (
    <Controller
      control={form.control}
      name="documentDetails"
      render={({ field }) => {
        const details = field.value ?? [];
        const valueOf = (line: string) => details.find((detail) => detail.document === line) ?? { document: line, ...EMPTY };
        const indexOf = (line: string) => details.findIndex((detail) => detail.document === line);
        const change = (line: string, patch: Partial<DocumentDetailValues>) => {
          const next = details.filter((detail) => detail.document !== line);
          next.push({ ...valueOf(line), ...patch, document: line });
          field.onChange(next);
        };
        return (
          <details className="rounded-control border border-line px-4 py-3" open={details.some((detail) => lines.includes(detail.document))}>
            <summary className="cursor-pointer text-sm font-medium text-foreground">
              {t('documents.editorTitle', 'Details for each document (optional)')}
            </summary>
            <p className="mt-2 text-sm leading-6 text-secondary">
              {t('documents.editorHint', 'Say only what the organization requires: original or copy, how many copies, validity and a link to the approved blank form if one is published. Leave unknown fields empty.')}
            </p>
            {lines.length === 0 ? (
              <p className="mt-3 text-sm text-secondary">{t('documents.editorEmpty', 'Add the required documents above, one per line, to describe them here.')}</p>
            ) : (
              <ol className="mt-4 space-y-5">
                {lines.map((line) => {
                  const value = valueOf(line);
                  const error = errors?.[indexOf(line)];
                  return (
                    <li key={line} className="space-y-3 border-t border-line pt-4 first:border-t-0 first:pt-0">
                      <p className="font-medium text-foreground wrap-anywhere">{line}</p>
                      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_8rem]">
                        <Field label={t('documents.form', 'Original or copy')}>
                          {(control) => (
                            <Select
                              {...control}
                              size="sm"
                              disabled={disabled}
                              value={value.form}
                              onValueChange={(form) => change(line, { form: form as DocumentDetailValues['form'] })}
                              options={[{ value: '', label: t('documents.formUnknown', 'Not specified') },
                                ...DOCUMENT_FORMS.map((form) => ({ value: form, label: t(`documents.form.${form}`, documentFormLabels[form]) }))]}
                            />
                          )}
                        </Field>
                        <Field label={t('documents.copies', 'Copies')} error={error?.copies?.message}>
                          {(control) => (
                            <Input {...control} type="number" min={1} max={COPIES_MAX} inputMode="numeric" disabled={disabled}
                              value={value.copies} onChange={(event) => change(line, { copies: event.target.value })} />
                          )}
                        </Field>
                        <Field label={t('documents.validity', 'Validity')} hint={t('documents.validityHint', 'For example: issued within the last 30 days')}>
                          {(control) => (
                            <Input {...control} maxLength={VALIDITY_MAX} disabled={disabled} autoComplete="off"
                              value={value.validity} onChange={(event) => change(line, { validity: event.target.value })} />
                          )}
                        </Field>
                        <Field label={t('documents.notes', 'Preparation notes')}>
                          {(control) => (
                            <Input {...control} maxLength={NOTES_MAX} disabled={disabled} autoComplete="off"
                              value={value.notes} onChange={(event) => change(line, { notes: event.target.value })} />
                          )}
                        </Field>
                        <Field label={t('documents.formLinkLabel', 'Approved blank form (link, optional)')} error={error?.formUrl?.message}
                          hint={t('documents.formLinkHint', 'Only a form the organization has approved and published.')} className="sm:col-span-2">
                          {(control) => (
                            <Input {...control} type="url" inputMode="url" placeholder="https://" maxLength={SOURCE_URL_MAX} disabled={disabled}
                              autoComplete="off" value={value.formUrl} onChange={(event) => change(line, { formUrl: event.target.value })} />
                          )}
                        </Field>
                      </div>
                    </li>
                  );
                })}
              </ol>
            )}
          </details>
        );
      }}
    />
  );
}
