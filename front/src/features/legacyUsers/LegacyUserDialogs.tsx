import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { UserCogIcon, UserPlusIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { CheckboxList } from '../../components/ui/CheckboxList';
import { useI18n } from '../../contexts/i18n';
import { applyServerErrors } from '../../lib/forms';
import type { LegacyUser, Organization, RoleEntity } from '../../types/api';
import { roleLabel } from '../../utils/format';
import { PersonFields } from '../staff/PersonFields';
import { useCreateLegacyUser, useUpdateLegacyUser } from './queries';
import { legacyUserCreateSchema, legacyUserEditSchema, type LegacyUserCreateValues, type LegacyUserEditValues } from './schema';

const CREATE_FORM_ID = 'legacy-user-create-form';
const EDIT_FORM_ID = 'legacy-user-edit-form';

interface CreateDialogProps {
  open: boolean;
  roles: RoleEntity[];
  organizations: Organization[];
  onClose: () => void;
}

export function LegacyUserCreateDialog({ open, roles, organizations, onClose }: CreateDialogProps) {
  const { t } = useI18n();
  const create = useCreateLegacyUser();
  const schema = useMemo(() => legacyUserCreateSchema(t), [t]);
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LegacyUserCreateValues>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '', password: '', phone: '', roleId: '', organizationIds: [] },
  });

  useEffect(() => {
    if (open) reset({ firstName: '', lastName: '', email: '', password: '', phone: '', roleId: '', organizationIds: [] });
  }, [open, reset]);

  const submit = handleSubmit(async (values) => {
    try {
      await create.mutateAsync({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
        phone: values.phone,
        roleId: Number(values.roleId),
        organizationIds: values.organizationIds,
      });
      toast.success(t('toast.created'));
      onClose();
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t);
    }
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      closeDisabled={isSubmitting}
      icon={<UserPlusIcon className="h-5 w-5" aria-hidden="true" />}
      title={`${t('action.create')} — ${t('legacy.badge')}`}
      description={t('legacy.notice')}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>{t('action.cancel')}</Button>
          <Button type="submit" form={CREATE_FORM_ID} loading={isSubmitting} icon={<UserPlusIcon />}>{t('action.create')}</Button>
        </>
      }
    >
      <form id={CREATE_FORM_ID} onSubmit={submit} noValidate className="grid gap-5 sm:grid-cols-2">
        <PersonFields register={register} errors={errors} />
        <Field label={t('field.email')} error={errors.email?.message} required>
          {(fieldControl) => <Input {...fieldControl} type="email" autoComplete="email" {...register('email')} />}
        </Field>
        <Field label={t('field.password')} error={errors.password?.message} hint={t('security.passwordRule')} required>
          {(fieldControl) => <Input {...fieldControl} type="password" autoComplete="new-password" minLength={8} maxLength={100} {...register('password')} />}
        </Field>
        <Field label={t('field.phone')} error={errors.phone?.message} hint={t('field.optional')}>
          {(fieldControl) => <Input {...fieldControl} type="tel" autoComplete="tel" maxLength={20} placeholder="+998 90 000 00 00" {...register('phone')} />}
        </Field>
        <Field label={t('field.role')} error={errors.roleId?.message} required>
          {(fieldControl) => (
            <Controller
              control={control}
              name="roleId"
              render={({ field }) => (
                <Select
                  {...fieldControl}
                  ref={field.ref}
                  value={field.value}
                  onValueChange={field.onChange}
                  onBlur={field.onBlur}
                  placeholder={t('action.select', 'Select role')}
                  options={roles.map((role) => ({ value: String(role.id), label: roleLabel(role.name, t) }))}
                />
              )}
            />
          )}
        </Field>
        <Controller
          control={control}
          name="organizationIds"
          render={({ field, fieldState }) => (
            <CheckboxList
              className="sm:col-span-2"
              legend={t('field.organizations')}
              options={organizations.map((organization) => ({ value: organization.id, label: `#${organization.id} — ${organization.name}` }))}
              selected={field.value}
              onChange={field.onChange}
              error={fieldState.error?.message}
              hint={t('field.optional')}
              required={false}
              columns={2}
            />
          )}
        />
      </form>
    </Modal>
  );
}

interface EditDialogProps {
  open: boolean;
  user: LegacyUser | null;
  onClose: () => void;
}

export function LegacyUserEditDialog({ open, user, onClose }: EditDialogProps) {
  const { t } = useI18n();
  const update = useUpdateLegacyUser();
  const schema = useMemo(() => legacyUserEditSchema(t), [t]);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LegacyUserEditValues>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '' },
  });

  useEffect(() => {
    if (open && user) reset({ firstName: user.firstName, lastName: user.lastName, email: user.email });
  }, [open, user, reset]);

  const submit = handleSubmit(async (values) => {
    if (!user) return;
    try {
      await update.mutateAsync({ id: user.id, payload: values });
      toast.success(t('toast.updated'));
      onClose();
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t);
    }
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      closeDisabled={isSubmitting}
      size="sm"
      icon={<UserCogIcon className="h-5 w-5" aria-hidden="true" />}
      title={t('action.edit')}
      description={t('legacy.updateNotice')}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>{t('action.cancel')}</Button>
          <Button type="submit" form={EDIT_FORM_ID} loading={isSubmitting}>{t('action.save')}</Button>
        </>
      }
    >
      <form id={EDIT_FORM_ID} onSubmit={submit} noValidate className="space-y-4">
        <PersonFields register={register} errors={errors} />
        <Field label={t('field.email')} error={errors.email?.message} required>
          {(fieldControl) => <Input {...fieldControl} type="email" autoComplete="email" {...register('email')} />}
        </Field>
      </form>
    </Modal>
  );
}
