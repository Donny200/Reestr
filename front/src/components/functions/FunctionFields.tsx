import { Fragment } from 'react';
import { Controller, type UseFormReturn } from 'react-hook-form';
import { DocumentDetailsEditor } from './DocumentDetailsEditor';
import { Field } from '../ui/Field';
import { Input, Textarea } from '../ui/Input';
import { Select } from '../ui/Select';
import { useI18n } from '../../contexts/i18n';
import { localizedText } from '../../utils/translations';
import { DESCRIPTION_MAX, NAME_MAX, REQUIREMENTS_MAX, SOURCE_URL_MAX } from '../../features/functions/schema';
import { INSTRUCTION_FIELDS } from '../../utils/instructions';
import { fieldLabelClass } from '../ui/Field';
import type { FunctionCategory, FunctionFormValues, FunctionOrganization } from '../../types/adminFunctions';

interface Props {
  form: UseFormReturn<FunctionFormValues>;
  organizations: FunctionOrganization[];
  categories: FunctionCategory[];
  disabled?: boolean;
}

export function FunctionFields({ form, organizations, categories, disabled = false }: Props) {
  const { t, locale, available } = useI18n();
  const { register, control, watch, formState: { errors } } = form;
  const sourceLanguage = watch('sourceLanguage');
  const languages = available.some((language) => language.code === sourceLanguage)
    ? available
    : [...available, { code: sourceLanguage, label: sourceLanguage }];

  return (
    <fieldset disabled={disabled} className="grid min-w-0 gap-5">
      <Field label={t('field.name')} required error={errors.name?.message}>
        {(fieldControl) => <Input {...fieldControl} maxLength={NAME_MAX} autoComplete="off" {...register('name')} />}
      </Field>
      <Field label={t('field.description')} required error={errors.description?.message}>
        {(fieldControl) => <Textarea {...fieldControl} rows={4} maxLength={DESCRIPTION_MAX} {...register('description')} />}
      </Field>
      <Field label={t('field.requirements')} hint={t('fnAdmin.requirementsHint')} error={errors.requirements?.message}>
        {(fieldControl) => <Textarea {...fieldControl} rows={4} maxLength={REQUIREMENTS_MAX} {...register('requirements')} />}
      </Field>
      <div className="grid gap-5 sm:grid-cols-2">
        <Field label={t('field.organization')} required error={errors.organizationId?.message}>
          {(fieldControl) => (
            <Controller
              control={control}
              name="organizationId"
              render={({ field }) => (
                <Select
                  {...fieldControl}
                  ref={field.ref}
                  disabled={disabled}
                  value={field.value}
                  onValueChange={field.onChange}
                  onBlur={field.onBlur}
                  placeholder={t('fnAdmin.choose')}
                  options={organizations.map((organization) => ({ value: String(organization.id), label: organization.name }))}
                />
              )}
            />
          )}
        </Field>
        <Field label={t('field.category')} error={errors.categoryId?.message}>
          {(fieldControl) => (
            <Controller
              control={control}
              name="categoryId"
              render={({ field }) => (
                <Select
                  {...fieldControl}
                  ref={field.ref}
                  disabled={disabled}
                  value={field.value}
                  onValueChange={field.onChange}
                  onBlur={field.onBlur}
                  options={[
                    { value: '', label: t('fnAdmin.noCategory') },
                    ...categories.map((category) => ({
                      value: String(category.id),
                      label: localizedText(category.name, category.nameTranslations, locale),
                    })),
                  ]}
                />
              )}
            />
          )}
        </Field>
      </div>
      <fieldset className="grid min-w-0 gap-5 rounded-card-sm border border-line p-4 sm:p-5">
        <legend className={`${fieldLabelClass} px-1`}>{t('instructions.editorTitle', 'Structured instructions')}</legend>
        <p className="text-sm leading-6 text-secondary">
          {t('instructions.editorHint', 'Fill in only what the official source confirms. Leave a field empty if it is unknown; visitors will see that it is not provided yet.')}
        </p>
        {INSTRUCTION_FIELDS.map((field) => (
          <Fragment key={field.key}>
          <Field
            label={t(field.labelKey, field.label)}
            hint={field.list ? t('instructions.oneItemPerLine', 'One item per line.') : undefined}
            error={errors[field.key]?.message}
          >
            {(fieldControl) =>
              field.max <= 500 ? (
                <Input {...fieldControl} maxLength={field.max} autoComplete="off" {...register(field.key)} />
              ) : (
                <Textarea {...fieldControl} rows={field.list ? 5 : 3} maxLength={field.max} {...register(field.key)} />
              )
            }
          </Field>
          {field.key === 'requiredDocuments' && <DocumentDetailsEditor form={form} disabled={disabled} />}
          </Fragment>
        ))}
        <Field
          label={t('verification.officialSourceUrl', 'External source link (optional)')}
          hint={t('verification.officialSourceHint', 'Optional. Add a link to an official page only if a useful source exists; this site is where the information is maintained.')}
          error={errors.officialSourceUrl?.message}
        >
          {(fieldControl) => (
            <Input {...fieldControl} type="url" inputMode="url" maxLength={SOURCE_URL_MAX} placeholder="https://" autoComplete="off" {...register('officialSourceUrl')} />
          )}
        </Field>
      </fieldset>
      <Field label={t('fnAdmin.sourceLanguage')} required error={errors.sourceLanguage?.message}>
        {(fieldControl) => (
          <Controller
            control={control}
            name="sourceLanguage"
            render={({ field }) => (
              <Select
                {...fieldControl}
                ref={field.ref}
                disabled={disabled}
                value={field.value}
                onValueChange={field.onChange}
                onBlur={field.onBlur}
                options={languages.map((language) => ({ value: language.code, label: language.label }))}
              />
            )}
          />
        )}
      </Field>
    </fieldset>
  );
}
