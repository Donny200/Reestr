import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Building2Icon } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Field, fieldLabelClass } from '../../components/ui/Field';
import { Input, Textarea } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors, withoutPrefix } from '../../lib/forms';
import type { Organization } from '../../types/api';
import { useRegions } from '../reference/queries';
import { organizationFormOf, organizationPayloadOf, organizationSchema, type OrganizationFormValues } from './schema';
import { useSaveOrganization } from './queries';

const FORM_ID = 'organization-form';

interface OrganizationFormDialogProps {
  open: boolean;
  organization: Organization | null;
  onClose: () => void;
}

export function OrganizationFormDialog({ open, organization, onClose }: OrganizationFormDialogProps) {
  const { t } = useI18n();
  const save = useSaveOrganization();
  const regions = useRegions();
  const schema = useMemo(() => organizationSchema(t), [t]);
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<OrganizationFormValues>({
    resolver: zodResolver(schema),
    defaultValues: organizationFormOf(null),
  });

  useEffect(() => {
    if (open) reset(organizationFormOf(organization));
  }, [open, organization, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await save.mutateAsync({ id: organization?.id, payload: organizationPayloadOf(values) });
      toast.success(t(organization ? 'toast.updated' : 'toast.created'));
      onClose();
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t, withoutPrefix('contact'));
    }
  });

  const regionOptions = [
    { value: '', label: t('contact.regionNone', 'Not specified') },
    ...(regions.data ?? []).map((region) => ({ value: region.code, label: region.name })),
  ];

  return (
    <Modal
      open={open}
      onClose={onClose}
      closeDisabled={isSubmitting}
      size="lg"
      icon={<Building2Icon className="h-5 w-5" aria-hidden="true" />}
      title={organization ? t('orgs.edit') : t('orgs.create')}
      description={organization ? `#${organization.id}` : t('home.orgsSubtitle')}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>{t('action.cancel')}</Button>
          <Button type="submit" form={FORM_ID} loading={isSubmitting}>{organization ? t('action.save') : t('action.create')}</Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={submit} noValidate className="space-y-5">
        <Field label={t('field.name')} error={errors.name?.message} required>
          {(fieldControl) => (
            <Input {...fieldControl} maxLength={150} autoComplete="organization" placeholder={t('orgs.namePlaceholder', 'Ministry of Justice')} {...register('name')} />
          )}
        </Field>
        <Field label={t('field.description')} error={errors.description?.message} hint={t('field.optional')}>
          {(fieldControl) => <Textarea {...fieldControl} rows={3} maxLength={500} {...register('description')} />}
        </Field>

        <fieldset className="grid min-w-0 gap-4 rounded-card-sm border border-line p-4 sm:grid-cols-2 sm:p-5">
          <legend className={`${fieldLabelClass} px-1`}>{t('contact.title', 'Contact and visiting information')}</legend>
          <p className="text-sm leading-6 text-secondary sm:col-span-2">
            {t('contact.editorHint', 'Enter only officially published details. Leave unknown fields empty; the public page shows them as not provided.')}
          </p>
          <Field label={t('contact.address', 'Address')} error={errors.address?.message} className="sm:col-span-2">
            {(fieldControl) => <Textarea {...fieldControl} rows={2} maxLength={500} autoComplete="street-address" {...register('address')} />}
          </Field>
          <Field label={t('contact.region', 'Region')} error={errors.regionCode?.message}>
            {(fieldControl) => (
              <Controller
                control={control}
                name="regionCode"
                render={({ field }) => (
                  <Select {...fieldControl} ref={field.ref} value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} options={regionOptions} />
                )}
              />
            )}
          </Field>
          <Field label={t('contact.phone', 'Phone')} error={errors.phone?.message}>
            {(fieldControl) => <Input {...fieldControl} type="tel" maxLength={40} autoComplete="tel" {...register('phone')} />}
          </Field>
          <Field label={t('contact.workingHours', 'Working hours')} error={errors.workingHours?.message} className="sm:col-span-2">
            {(fieldControl) => <Textarea {...fieldControl} rows={2} maxLength={500} {...register('workingHours')} />}
          </Field>
          <Field label={t('contact.latitude', 'Latitude')} error={errors.latitude?.message} hint={t('field.optional')}>
            {(fieldControl) => <Input {...fieldControl} inputMode="decimal" maxLength={20} placeholder="41.311" {...register('latitude')} />}
          </Field>
          <Field label={t('contact.longitude', 'Longitude')} error={errors.longitude?.message} hint={t('field.optional')}>
            {(fieldControl) => <Input {...fieldControl} inputMode="decimal" maxLength={20} placeholder="69.279" {...register('longitude')} />}
          </Field>
          <Field
            label={t('contact.mapUrl', 'Map link')}
            error={errors.mapUrl?.message}
            hint={t('contact.mapUrlHint', 'Optional link to a free map page. Do not use links that need an API key.')}
            className="sm:col-span-2"
          >
            {(fieldControl) => <Input {...fieldControl} type="url" inputMode="url" maxLength={500} placeholder="https://" {...register('mapUrl')} />}
          </Field>
          <Field
            label={t('verification.officialSourceUrl', 'External source link (optional)')}
            error={errors.officialSourceUrl?.message}
            hint={t('verification.officialSourceHint', 'Optional. Add a link to an official page only if a useful source exists; this site is where the information is maintained.')}
            className="sm:col-span-2"
          >
            {(fieldControl) => <Input {...fieldControl} type="url" inputMode="url" maxLength={500} placeholder="https://" {...register('officialSourceUrl')} />}
          </Field>
        </fieldset>
      </form>
    </Modal>
  );
}
