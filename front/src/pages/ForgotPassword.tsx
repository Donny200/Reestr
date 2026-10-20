import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowLeftIcon, MailCheckIcon } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Eyebrow } from '../components/ui/Eyebrow';
import { Field } from '../components/ui/Field';
import { Input } from '../components/ui/Input';
import { InlineAlert } from '../features/functions/InlineAlert';
import { AuthShell } from '../components/layout/AuthShell';
import { useI18n } from '../contexts/i18n';
import { emailPattern } from '../lib/validation';
import { usePasswordResetOptions } from '../features/passwordReset/queries';
import { requestPasswordReset } from '../services/authService';
import { errorMessage, statusOf, type Translate } from '../utils/errors';

const schema = (t: Translate) => z.object({ email: z.string().trim().min(1, t('validation.required')).regex(emailPattern, t('validation.email')) });

type Values = z.infer<ReturnType<typeof schema>>;

/** Self-service reset request. The answer is the same whether or not the address belongs to an account. */
export function ForgotPassword() {
  const { t } = useI18n();
  const options = usePasswordResetOptions();
  const [sent, setSent] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const resolver = useMemo(() => zodResolver(schema(t)), [t]);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver, defaultValues: { email: '' } });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await requestPasswordReset(values.email);
      setSent(true);
    } catch (error) {
      setFormError(statusOf(error) === 503 ? t('reset.unavailable', 'Self-service reset is not available. Ask an administrator to reset your password.') : errorMessage(error, t));
    }
  });

  const unavailable = options.data?.selfServiceAvailable === false;

  return (
    <AuthShell>
      <Eyebrow>{t('nav.admin')}</Eyebrow>
      <h1 className="mt-4 text-4xl font-semibold text-foreground">{t('reset.requestTitle', 'Reset your password')}</h1>
      <p className="mt-2 text-base leading-6 text-secondary">{t('reset.requestSubtitle', 'Enter your email. If an account exists, a reset link will be sent.')}</p>

      {sent ? (
        <InlineAlert tone="success" className="mt-6">
          <span className="flex items-start gap-2"><MailCheckIcon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            {t('reset.requestSent', 'If an account with this email exists, a reset link has been sent. Check your inbox.')}</span>
        </InlineAlert>
      ) : unavailable ? (
        <InlineAlert tone="warning" className="mt-6">{t('reset.unavailable', 'Self-service reset is not available. Ask an administrator to reset your password.')}</InlineAlert>
      ) : (
        <form className="mt-6 space-y-5" onSubmit={submit} noValidate>
          {formError && <InlineAlert tone="danger">{formError}</InlineAlert>}
          <Field label={t('field.email')} error={errors.email?.message} required>
            {(control) => <Input {...control} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
          </Field>
          <Button type="submit" variant="dark" size="lg" className="w-full" loading={isSubmitting} disabled={options.isPending}>
            {t('reset.send', 'Send reset link')}
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
