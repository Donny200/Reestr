import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ShieldCheckIcon } from 'lucide-react';
import { toast } from 'sonner';
import { AuthShell } from '../components/layout/AuthShell';
import { Button } from '../components/ui/Button';
import { Eyebrow } from '../components/ui/Eyebrow';
import { Field } from '../components/ui/Field';
import { Input } from '../components/ui/Input';
import { homeRouteForRole, useAuth } from '../contexts/auth';
import { useI18n } from '../contexts/i18n';
import { InlineAlert } from '../features/functions/InlineAlert';
import { MfaEnrollmentPanel, RecoveryCodes } from '../features/mfa/MfaEnrollmentPanel';
import { confirmMfaEnrollment, startMfaEnrollment, verifyMfa } from '../services/authService';
import type { AuthUser, MfaEnrollment } from '../types/api';
import { errorMessage, statusOf } from '../utils/errors';

interface ChallengeState {
  enrollmentRequired?: boolean;
  expiresAt?: string;
  from?: string;
}

/** Second step of sign-in: enter a code from the app (or a recovery code), or enroll when the role requires it. */
export function MfaChallenge() {
  const { t } = useI18n();
  const { completeSignIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state as ChallengeState | null) ?? {};
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [pendingUser, setPendingUser] = useState<AuthUser | null>(null);

  const finish = (user: AuthUser) => {
    completeSignIn(user);
    toast.success(t('login.success'));
    navigate(user.mustChangePassword ? '/settings/security' : state.from ?? homeRouteForRole(user.role), { replace: true });
  };

  const verify = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      finish(await verifyMfa(code.trim()));
    } catch (failure) {
      const status = statusOf(failure);
      setError(status === 401 ? t('mfa.invalidCode', 'The code is not valid or the sign-in attempt expired. Check the app and try again, or start over.') : errorMessage(failure, t));
    } finally {
      setBusy(false);
    }
  };

  const start = async () => {
    setBusy(true);
    setError(null);
    try {
      setEnrollment(await startMfaEnrollment());
    } catch (failure) {
      setError(errorMessage(failure, t));
    } finally {
      setBusy(false);
    }
  };

  const confirm = async (value: string) => {
    const result = await confirmMfaEnrollment(value);
    setRecoveryCodes(result.recoveryCodes);
    setPendingUser(result.user);
  };

  return (
    <AuthShell>
      <Eyebrow>{t('nav.admin')}</Eyebrow>
      <h1 className="mt-4 text-4xl font-semibold text-foreground">
        {state.enrollmentRequired ? t('mfa.enrollTitle', 'Set up two-factor authentication') : t('mfa.verifyTitle', 'Enter your verification code')}
      </h1>
      <p className="mt-2 text-base leading-6 text-secondary">
        {state.enrollmentRequired
          ? t('mfa.enrollRequired', 'Your account requires a second factor. Finish the setup to continue; no session exists until it is confirmed.')
          : t('mfa.verifySubtitle', 'Open your authenticator app and enter the 6-digit code, or use one of your recovery codes.')}
      </p>

      {error && <InlineAlert tone="danger" className="mt-6">{error}</InlineAlert>}

      {recoveryCodes ? (
        <div className="mt-6">
          <RecoveryCodes codes={recoveryCodes} onDone={() => pendingUser && finish(pendingUser)} />
        </div>
      ) : state.enrollmentRequired ? (
        <div className="mt-6">
          <MfaEnrollmentPanel enrollment={enrollment} starting={busy} onStart={start} onConfirm={confirm} confirming={busy} />
        </div>
      ) : (
        <form className="mt-6 space-y-5" onSubmit={verify} noValidate>
          <Field label={t('mfa.code', 'Verification code')} required hint={t('mfa.codeHint', '6 digits from the app, or a recovery code such as ABCDE-FGHJK.')}>
            {(control) => (
              <Input {...control} autoFocus autoComplete="one-time-code" inputMode="text" maxLength={16} value={code} onChange={(event) => setCode(event.target.value)} className="tracking-[0.2em]" />
            )}
          </Field>
          <Button type="submit" variant="dark" size="lg" className="w-full" icon={<ShieldCheckIcon />} loading={busy} disabled={code.trim().length < 6}>
            {t('mfa.verify', 'Verify and sign in')}
          </Button>
        </form>
      )}

      <p className="mt-5 text-sm">
        <Link to="/login" className="rounded-sm font-medium text-accent-text underline-offset-4 fine:hover:underline">{t('mfa.startOver', 'Back to sign-in')}</Link>
      </p>
    </AuthShell>
  );
}
