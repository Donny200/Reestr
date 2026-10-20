import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { PageHeader } from '../components/layout/PageHeader';
import { Avatar } from '../components/ui/Avatar';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Card, CardBody, CardFooter, CardHeader } from '../components/ui/Card';
import { Field } from '../components/ui/Field';
import { Input } from '../components/ui/Input';
import { useAuth } from '../contexts/auth';
import { useI18n } from '../contexts/i18n';
import { applyServerErrors } from '../lib/forms';
import { passwordField } from '../lib/validation';
import { changePassword } from '../services/authService';
import type { Translate } from '../utils/errors';
import { fullName, roleLabel } from '../utils/format';
import { MfaSettingsCard } from '../features/mfa/MfaSettingsCard';
import { SessionsCard } from '../features/sessions/SessionsCard';

const passwordSchema = (t: Translate) =>
  z
    .object({
      currentPassword: z.string().min(1, t('validation.required')),
      newPassword: passwordField(t),
      repeatPassword: z.string(),
    })
    .refine((values) => values.newPassword === values.repeatPassword, {
      message: t('security.mismatch'),
      path: ['repeatPassword'],
    });

type PasswordValues = z.infer<ReturnType<typeof passwordSchema>>;

const emptyValues: PasswordValues = { currentPassword: '', newPassword: '', repeatPassword: '' };

export function SecuritySettings() {
  const { t } = useI18n();
  const { user, refreshUser } = useAuth();
  const schema = useMemo(() => passwordSchema(t), [t]);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordValues>({ resolver: zodResolver(schema), defaultValues: emptyValues });

  const submit = handleSubmit(async (values) => {
    try {
      await changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword });
      await refreshUser();
      toast.success(t('security.success'));
      reset(emptyValues);
    } catch (error) {
      applyServerErrors(error, setError, (message) => toast.error(message), t);
    }
  });

  return (
    <div>
      <PageHeader eyebrow={t('nav.admin')} title={t('security.title')} description={t('security.passwordRule')} />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.5fr)_minmax(18rem,1fr)] lg:items-start">
        <div className="min-w-0 space-y-6">
        <Card>
          <CardHeader title={t('security.changePassword')} />
          <form onSubmit={submit} noValidate>
            <CardBody className="max-w-lg space-y-5">
              <Field label={t('security.currentPassword')} error={errors.currentPassword?.message} required>
                {(control) => <Input {...control} type="password" autoComplete="current-password" {...register('currentPassword')} />}
              </Field>
              <Field label={t('security.newPassword')} error={errors.newPassword?.message} hint={t('security.passwordRule')} required>
                {(control) => <Input {...control} type="password" autoComplete="new-password" minLength={8} maxLength={100} {...register('newPassword')} />}
              </Field>
              <Field label={t('security.repeatPassword')} error={errors.repeatPassword?.message} required>
                {(control) => <Input {...control} type="password" autoComplete="new-password" minLength={8} maxLength={100} {...register('repeatPassword')} />}
              </Field>
            </CardBody>
            <CardFooter>
              <Button type="submit" variant="dark" loading={isSubmitting}>{t('action.save')}</Button>
            </CardFooter>
          </form>
        </Card>
        {!user?.mustChangePassword && <MfaSettingsCard />}
        {!user?.mustChangePassword && <SessionsCard />}
        </div>

        <Card className="lg:sticky lg:top-24">
          <CardHeader title={t('admin.welcome')} />
          <CardBody>
            {user && (
              <>
                <div className="flex items-center gap-3 rounded-card-sm bg-surface p-3">
                  <Avatar user={user} size="lg" />
                  <span className="min-w-0">
                    <span className="block truncate text-base font-semibold text-foreground">{fullName(user)}</span>
                    <Badge size="sm" tone="accent" className="mt-1">{roleLabel(user.role, t)}</Badge>
                  </span>
                </div>
                <dl className="mt-5 divide-y divide-line text-sm">
                  <div className="py-3 first:pt-0">
                    <dt className="micro text-secondary">{t('field.fullName')}</dt>
                    <dd className="mt-1 font-medium text-foreground wrap-anywhere">{fullName(user)}</dd>
                  </div>
                  <div className="py-3">
                    <dt className="micro text-secondary">{t('field.email')}</dt>
                    <dd className="mt-1 font-medium text-foreground wrap-anywhere">{user.email}</dd>
                  </div>
                  <div className="py-3">
                    <dt className="micro text-secondary">{t('field.phone')}</dt>
                    <dd className="mt-1 font-medium text-foreground">{user.phone ?? '—'}</dd>
                  </div>
                  <div className="py-3 last:pb-0">
                    <dt className="micro text-secondary">{t('field.organizationIds')}</dt>
                    <dd className="mt-1 font-medium tabular-nums text-foreground">
                      {user.organizationIds.length > 0 ? user.organizationIds.join(', ') : '—'}
                    </dd>
                  </div>
                </dl>
              </>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
