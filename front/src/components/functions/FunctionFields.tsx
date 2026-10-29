import { Controller, type UseFormReturn } from 'react-hook-form';
import { DocumentDetailsEditor } from './DocumentDetailsEditor';
import { Reviewable } from './ReviewFeedback';
import { Field } from '../ui/Field';
import { Input, Textarea } from '../ui/Input';
import { Select } from '../ui/Select';
import { useI18n } from '../../contexts/i18n';
import { localizedText } from '../../utils/translations';
import { DESCRIPTION_MAX, NAME_MAX, REQUIREMENTS_MAX, RISK_NOTES_MAX, SOURCE_URL_MAX } from '../../features/functions/schema';
import { reviewPathOf } from '../../features/functions/wizard';
import { useFieldLabel } from '../../features/functions/reviewLabels';
import { INSTRUCTION_FIELDS } from '../../utils/instructions';
import {
  DELIVERY_CHANNELS, PROVISION_LEVELS, RISK_LEVELS,
  type FunctionCategory, type FunctionFormValues, type FunctionOrganization, type WizardStep,
} from '../../types/adminFunctions';
import type { InstructionField } from '../../types/api';

interface Props {
  form: UseFormReturn<FunctionFormValues>;
  step: WizardStep;
  organizations: FunctionOrganization[];
  categories: FunctionCategory[];
  disabled?: boolean;
}

type SelectField = 'organizationId' | 'categoryId' | 'sourceLanguage' | 'deliveryChannel' | 'provisionLevel' | 'riskLevel';

/** The fields of one editor step. Every field is reviewable: reviewer notes appear directly above it. */
export function FunctionFields({ form, step, organizations, categories, disabled = false }: Props) {
  const { t, locale, available } = useI18n();
  const { register, control, watch, formState: { errors } } = form;
  const label = useFieldLabel();
  const sourceLanguage = watch('sourceLanguage');
  const deliveryChannel = watch('deliveryChannel');
  const riskLevel = watch('riskLevel');
  const languages = available.some((language) => language.code === sourceLanguage)
    ? available
    : [...available, { code: sourceLanguage, label: sourceLanguage }];

  const select = (name: SelectField, options: { value: string; label: string }[], { required = false, placeholder }: { required?: boolean; placeholder?: string } = {}) => (
    <Reviewable path={reviewPathOf(name)} label={label(name)}>
      <Field label={label(name)} required={required} error={errors[name]?.message}>
        {(fieldControl) => (
          <Controller
            control={control}
            name={name}
            render={({ field }) => (
              <Select
                {...fieldControl}
                ref={field.ref}
                disabled={disabled}
                value={field.value}
                onValueChange={field.onChange}
                onBlur={field.onBlur}
                placeholder={placeholder}
                options={options}
              />
            )}
          />
        )}
      </Field>
    </Reviewable>
  );

  const instruction = (key: InstructionField) => {
    const meta = INSTRUCTION_FIELDS.find((item) => item.key === key);
    if (!meta) return null;
    return (
      <Reviewable path={reviewPathOf(key)} label={label(key)}>
        <Field
          label={label(key)}
          hint={meta.list ? t('instructions.oneItemPerLine') : undefined}
          error={errors[key]?.message}
        >
          {(fieldControl) =>
            meta.max <= 500 ? (
              <Input {...fieldControl} disabled={disabled} maxLength={meta.max} autoComplete="off" {...register(key)} />
            ) : (
              <Textarea {...fieldControl} disabled={disabled} rows={meta.list ? 5 : 3} maxLength={meta.max} {...register(key)} />
            )
          }
        </Field>
      </Reviewable>
    );
  };

  const notSpecified = { value: '', label: t('profile.notSpecified') };

  return (
    <div className="grid min-w-0 gap-5">
      {step === 'general' && (
        <>
          <Reviewable path="name" label={label('name')}>
            <Field label={label('name')} required error={errors.name?.message}>
              {(fieldControl) => <Input {...fieldControl} disabled={disabled} maxLength={NAME_MAX} autoComplete="off" {...register('name')} />}
            </Field>
          </Reviewable>
          <Reviewable path="description" label={label('description')}>
            <Field label={label('description')} required error={errors.description?.message}>
              {(fieldControl) => <Textarea {...fieldControl} disabled={disabled} rows={4} maxLength={DESCRIPTION_MAX} {...register('description')} />}
            </Field>
          </Reviewable>
          <div className="grid gap-5 sm:grid-cols-2">
            {select('organizationId', organizations.map((organization) => ({ value: String(organization.id), label: organization.name })),
              { required: true, placeholder: t('fnAdmin.choose') })}
            {select('categoryId', [
              { value: '', label: t('fnAdmin.noCategory') },
              ...categories.map((category) => ({ value: String(category.id), label: localizedText(category.name, category.nameTranslations, locale) })),
            ])}
          </div>
          {select('sourceLanguage', languages.map((language) => ({ value: language.code, label: language.label })), { required: true })}
        </>
      )}

      {step === 'mechanism' && (
        <>
          <p className="text-sm leading-6 text-secondary">{t('instructions.editorHint')}</p>
          {instruction('whoCanUse')}
          {instruction('steps')}
          {instruction('requiredDocuments')}
          <DocumentDetailsEditor form={form} disabled={disabled} />
          {instruction('processingTime')}
          {instruction('fee')}
        </>
      )}

      {step === 'digitalization' && (
        <>
          {select('deliveryChannel', [notSpecified, ...DELIVERY_CHANNELS.map((value) => ({ value, label: t(`profile.deliveryChannel.${value}`) }))])}
          <Reviewable path={reviewPathOf('onlineServiceUrl')} label={label('onlineServiceUrl')}>
            <Field
              label={label('onlineServiceUrl')}
              required={deliveryChannel === 'ONLINE' || deliveryChannel === 'ONLINE_AND_IN_PERSON'}
              hint={t('profile.onlineServiceUrlHint')}
              error={errors.onlineServiceUrl?.message}
            >
              {(fieldControl) => (
                <Input {...fieldControl} disabled={disabled} type="url" inputMode="url" maxLength={SOURCE_URL_MAX} placeholder="https://" autoComplete="off" {...register('onlineServiceUrl')} />
              )}
            </Field>
          </Reviewable>
          {instruction('whereHowToApply')}
        </>
      )}

      {step === 'decentralization' && (
        <>
          <p className="text-sm leading-6 text-secondary">{t('profile.staffOnly')}</p>
          <div className="grid gap-5 sm:grid-cols-2">
            {select('provisionLevel', [notSpecified, ...PROVISION_LEVELS.map((value) => ({ value, label: t(`profile.provisionLevel.${value}`) }))])}
            {select('riskLevel', [notSpecified, ...RISK_LEVELS.map((value) => ({ value, label: t(`profile.riskLevel.${value}`) }))])}
          </div>
          <Reviewable path={reviewPathOf('riskNotes')} label={label('riskNotes')}>
            <Field label={label('riskNotes')} required={riskLevel === 'HIGH'} hint={t('profile.riskNotesHint')} error={errors.riskNotes?.message}>
              {(fieldControl) => <Textarea {...fieldControl} disabled={disabled} rows={4} maxLength={RISK_NOTES_MAX} {...register('riskNotes')} />}
            </Field>
          </Reviewable>
        </>
      )}

      {step === 'other' && (
        <>
          <Reviewable path="requirements" label={label('requirements')}>
            <Field label={label('requirements')} hint={t('fnAdmin.requirementsHint')} error={errors.requirements?.message}>
              {(fieldControl) => <Textarea {...fieldControl} disabled={disabled} rows={4} maxLength={REQUIREMENTS_MAX} {...register('requirements')} />}
            </Field>
          </Reviewable>
          <Reviewable path="officialSourceUrl" label={label('officialSourceUrl')}>
            <Field label={label('officialSourceUrl')} hint={t('verification.officialSourceHint')} error={errors.officialSourceUrl?.message}>
              {(fieldControl) => (
                <Input {...fieldControl} disabled={disabled} type="url" inputMode="url" maxLength={SOURCE_URL_MAX} placeholder="https://" autoComplete="off" {...register('officialSourceUrl')} />
              )}
            </Field>
          </Reviewable>
        </>
      )}
    </div>
  );
}
