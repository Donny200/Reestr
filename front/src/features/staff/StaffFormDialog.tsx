import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { UserPlusIcon, UserCogIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Checkbox, Input } from '../../components/ui/Input';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors } from '../../lib/forms';
import type { Organization, StaffUser } from '../../types/api';
import { OrganizationsField } from './OrganizationsField';
import { PersonFields } from './PersonFields';
import { useStaffMutation } from './queries';
import { staffCreateSchema, staffEditSchema, type StaffCreateFormValues, type StaffEditFormValues } from './schema';
import type { StaffConfig } from './types';

const CREATE_FORM_ID = 'staff-create-form';
const EDIT_FORM_ID = 'staff-edit-form';

const organizationField = (field: string) => (field === 'organizationId' ? 'organizationIds' : field);

interface DialogProps {
  config: StaffConfig;
  open: boolean;
  organizations: Organization[];
  onClose: () => void;
}

export function StaffCreateDialog({ config, open, organizations, onClose }: DialogProps) {
  const { t } = useI18n();
  const create = useStaffMutation(config.create);
  const schema = useMemo(() => staffCreateSchema(t), [t]);
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<StaffCreateFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '', password: '', phone: '', organizationIds: [] },
  });

  useEffect(() => {
    if (open) reset({ firstName: '', lastName: '', email: '', password: '', phone: '', organizationIds: [] });
  }, [open, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await create.mutateAsync(values);
      toast.success(t('toast.created'));
      onClose();
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t, organizationField);
    }
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      closeDisabled={isSubmitting}
      icon={<UserPlusIcon className="h-5 w-5" aria-hidden="true" />}
      title={t(config.createKey)}
      description={config.descriptionKey ? t(config.descriptionKey) : undefined}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>{t('action.cancel')}</Button>
          <Button type="submit" form={CREATE_FORM_ID} loading={isSubmitting} icon={<UserPlusIcon />}>{t('action.create')}</Button>
        </>
      }
    >
      <form id={CREATE_FORM_ID} onSubmit={submit} noValidate className="grid gap-5 sm:grid-cols-2">
        <PersonFields register={register} errors={errors} />
        <Field label={t('field.email')} error={errors.email?.message} required className="sm:col-span-2">
          {(fieldControl) => <Input {...fieldControl} type="email" autoComplete="email" {...register('email')} />}
        </Field>
        <Field label={t('field.password')} error={errors.password?.message} hint={t('security.passwordRule')} required>
          {(fieldControl) => <Input {...fieldControl} type="password" autoComplete="new-password" minLength={8} maxLength={100} {...register('password')} />}
        </Field>
        <Field label={t('field.phone')} error={errors.phone?.message} hint={t('field.optional')}>
          {(fieldControl) => <Input {...fieldControl} type="tel" autoComplete="tel" maxLength={20} placeholder="+998 90 000 00 00" {...register('phone')} />}
        </Field>
        <Controller
          control={control}
          name="organizationIds"
          render={({ field, fieldState }) => (
            <OrganizationsField
              className="sm:col-span-2"
              mode={config.organizations}
              organizations={organizations}
              value={field.value}
              onChange={field.onChange}
              error={fieldState.error?.message}
              hint={config.organizations === 'multiple' ? t('staff.orgsMandatory') : undefined}
            />
          )}
        />
      </form>
    </Modal>
  );
}

interface EditDialogProps extends DialogProps {
  user: StaffUser | null;
}

export function StaffEditDialog({ config, open, organizations, user, onClose }: EditDialogProps) {
  const { t } = useI18n();
  const requireOrganizations = config.organizations === 'multiple';
  const update = useStaffMutation(({ id, values }: { id: number; values: StaffEditFormValues }) => config.update(id, values));
  const schema = useMemo(() => staffEditSchema(t, requireOrganizations), [t, requireOrganizations]);
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<StaffEditFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', phone: '', organizationIds: [], enabled: true },
  });

  useEffect(() => {
    if (open && user) {
      reset({
        firstName: user.firstName,
        lastName: user.lastName,
        phone: user.phone ?? '',
        organizationIds: requireOrganizations ? [...user.organizationIds] : user.organizationIds.slice(0, 1),
        enabled: user.enabled,
      });
    }
  }, [open, user, requireOrganizations, reset]);

  const submit = handleSubmit(async (values) => {
    if (!user) return;
    try {
      await update.mutateAsync({ id: user.id, values });
      toast.success(t('toast.updated'));
      onClose();
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t, organizationField);
    }
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      closeDisabled={isSubmitting}
      icon={<UserCogIcon className="h-5 w-5" aria-hidden="true" />}
      title={t('action.edit')}
      description={user ? `#${user.id} · ${user.email}` : undefined}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>{t('action.cancel')}</Button>
          <Button type="submit" form={EDIT_FORM_ID} loading={isSubmitting}>{t('action.save')}</Button>
        </>
      }
    >
      <form id={EDIT_FORM_ID} onSubmit={submit} noValidate className="grid gap-5 sm:grid-cols-2">
        <PersonFields register={register} errors={errors} />
        <Field label={t('field.phone')} error={errors.phone?.message} className="sm:col-span-2">
          {(fieldControl) => <Input {...fieldControl} type="tel" autoComplete="tel" maxLength={20} {...register('phone')} />}
        </Field>
        <Controller
          control={control}
          name="organizationIds"
          render={({ field, fieldState }) => (
            <OrganizationsField
              className="sm:col-span-2"
              mode={config.organizations}
              organizations={organizations}
              value={field.value}
              onChange={field.onChange}
              error={fieldState.error?.message}
              required={requireOrganizations}
              hint={requireOrganizations ? t('staff.orgsMandatory') : undefined}
            />
          )}
        />
        <Controller
          control={control}
          name="enabled"
          render={({ field }) => (
            <label className="flex min-h-11 cursor-pointer items-center gap-3 rounded-control border border-line bg-surface px-3 text-sm font-medium text-foreground sm:col-span-2">
              <Checkbox checked={field.value} onChange={(event) => field.onChange(event.target.checked)} />
              {t('field.enabled')}
            </label>
          )}
        />
      </form>
    </Modal>
  );
}
