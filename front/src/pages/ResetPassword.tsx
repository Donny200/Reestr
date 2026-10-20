import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowLeftIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button';
import { Eyebrow } from '../components/ui/Eyebrow';
import { Field } from '../components/ui/Field';
import { Input } from '../components/ui/Input';
import { InlineAlert } from '../features/functions/InlineAlert';
import { AuthShell } from '../components/layout/AuthShell';
import { useI18n } from '../contexts/i18n';
import { applyServerErrors } from '../lib/forms';
import { passwordField } from '../lib/validation';
import { completePasswordReset } from '../services/authService';
import { statusOf, type Translate } from '../utils/errors';

const schema = (t: Translate) =>
  z
    .object({ newPassword: passwordField(t), repeatPassword: z.string() })
    .refine((values) => values.newPassword === values.repeatPassword, { message: t('security.mismatch'), path: ['repeatPassword'] });

type Values = z.infer<ReturnType<typeof schema>>;

/** Completes a reset from a single-use link (admin-issued or e-mailed). The token never leaves the query string. */
export function ResetPassword() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [formError, setFormError] = useState<string | null>(null);
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({ resolver, defaultValues: { newPassword: '', repeatPassword: '' } });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await completePasswordReset(token, values.newPassword);
      toast.success(t('reset.success', 'Password reset. Sign in with your new password.'));
      navigate('/login', { replace: true });
    } catch (error) {
      if (statusOf(error) === 400) {
        applyServerErrors(error, setError, () => setFormError(t('reset.invalid', 'This reset link is invalid or has expired. Request a new one.')), t);
      } else {
        applyServerErrors(error, setError, setFormError, t);
      }
    }
  });

  return (
    <AuthShell>
      <Eyebrow>{t('nav.admin')}</Eyebrow>
      <h1 className="mt-4 text-4xl font-semibold text-foreground">{t('reset.completeTitle', 'Choose a new password')}</h1>
      <p className="mt-2 text-base leading-6 text-secondary">{t('security.passwordRule')}</p>

      {!token ? (
        <InlineAlert tone="danger" className="mt-6">{t('reset.missingToken', 'This link is incomplete. Open the full reset link you received.')}</InlineAlert>
      ) : (
        <form className="mt-6 space-y-5" onSubmit={submit} noValidate>
          {formError && <InlineAlert tone="danger">{formError}</InlineAlert>}
          <Field label={t('security.newPassword')} error={errors.newPassword?.message} hint={t('security.passwordRule')} required>
            {(control) => <Input {...control} type="password" autoComplete="new-password" minLength={8} maxLength={100} {...register('newPassword')} />}
          </Field>
          <Field label={t('security.repeatPassword')} error={errors.repeatPassword?.message} required>
            {(control) => <Input {...control} type="password" autoComplete="new-password" minLength={8} maxLength={100} {...register('repeatPassword')} />}
          </Field>
          <Button type="submit" variant="dark" size="lg" className="w-full" loading={isSubmitting}>
            {t('reset.complete', 'Set new password')}
          </Button>
        </form>
      )}

      <Link to="/login" className="mt-6 inline-flex min-h-10 items-center gap-2 rounded-pill text-sm font-medium text-secondary transition-colors duration-snap fine:hover:text-foreground">
        <ArrowLeftIcon className="h-4 w-4 rtl:-scale-x-100" aria-hidden="true" />
        {t('reset.backToLogin', 'Back to sign in')}
      </Link>
    </AuthShell>
  );
}
