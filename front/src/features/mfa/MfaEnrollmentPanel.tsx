import { useMemo, useState, type FormEvent } from 'react';
import { CopyIcon, KeyRoundIcon, ShieldCheckIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { useI18n } from '../../contexts/i18n';
import { InlineAlert } from '../functions/InlineAlert';
import type { MfaEnrollment } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { copyText } from '../../utils/share';

function groupSecret(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim();
}

interface MfaEnrollmentPanelProps {
  enrollment: MfaEnrollment | null;
  starting: boolean;
  onStart: () => Promise<void>;
  onConfirm: (code: string) => Promise<void>;
  confirming: boolean;
}

/** Shows the one-time secret, lets the user add it to an authenticator app and confirms with a first code. */
export function MfaEnrollmentPanel({ enrollment, starting, onStart, onConfirm, confirming }: MfaEnrollmentPanelProps) {
  const { t } = useI18n();
  const [code, setCode] = useState('');
  const [error, setError] = useState<unknown>(null);
  const grouped = useMemo(() => (enrollment ? groupSecret(enrollment.secret) : ''), [enrollment]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      await onConfirm(code.trim());
      setCode('');
    } catch (failure) {
      setError(failure);
    }
  };

  if (!enrollment) {
    return (
      <div className="space-y-4">
        <p className="text-sm leading-6 text-secondary">
          {t('mfa.enrollIntro', 'Install an authenticator app (for example Google Authenticator, Microsoft Authenticator, FreeOTP or Aegis), then generate your personal key.')}
        </p>
        <Button variant="dark" icon={<KeyRoundIcon />} loading={starting} onClick={() => void onStart()}>
          {t('mfa.start', 'Generate key')}
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-5" noValidate>
      <ol className="list-decimal space-y-3 ps-5 text-sm leading-6 text-foreground">
        <li>
          {t('mfa.stepAdd', 'In your authenticator app, add an account and enter this key (or open the link on a phone that has the app):')}
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <code className="rounded-control bg-surface px-3 py-2 text-base font-semibold tracking-[0.15em] text-foreground" aria-label={t('mfa.secretLabel', 'Secret key')}>{grouped}</code>
            <Button
              variant="outline"
              size="sm"
              icon={<CopyIcon />}
              onClick={() => void copyText(enrollment.secret).then((ok) => toast[ok ? 'success' : 'error'](ok ? t('share.copied', 'Link copied to the clipboard') : t('share.manualHint', 'Your browser does not allow automatic copying. Select the link and copy it.')))}
            >
              {t('mfa.copyKey', 'Copy key')}
            </Button>
            <a href={enrollment.otpauthUri} className="rounded-sm text-sm font-medium text-accent-text underline-offset-4 fine:hover:underline">
              {t('mfa.openInApp', 'Open in authenticator app')}
            </a>
          </div>
          <p className="mt-1 text-xs text-secondary">{t('mfa.accountLabel', 'Account')}: {enrollment.account} · {t('mfa.issuerLabel', 'Issuer')}: {enrollment.issuer} · SHA1, 6 {t('mfa.digits', 'digits')}, 30 s</p>
        </li>
        <li>{t('mfa.stepConfirm', 'Enter the 6-digit code the app shows now to confirm the enrollment.')}</li>
      </ol>
      <Field label={t('mfa.code', 'Verification code')} required>
        {(control) => (
          <Input {...control} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9 ]*" maxLength={8} value={code} onChange={(event) => setCode(event.target.value)} className="max-w-xs tracking-[0.25em]" />
        )}
      </Field>
      {Boolean(error) && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
      <Button type="submit" variant="dark" icon={<ShieldCheckIcon />} loading={confirming} disabled={code.replace(/\s/g, '').length < 6}>
        {t('mfa.confirm', 'Confirm and enable')}
      </Button>
    </form>
  );
}

/** Recovery codes are shown exactly once; the page never requests them again. */
export function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone?: () => void }) {
  const { t } = useI18n();
  const text = codes.join('\n');
  return (
    <div className="space-y-4" role="region" aria-labelledby="recovery-codes-title">
      <InlineAlert tone="warning">
        <span className="font-semibold">{t('mfa.recoveryTitle', 'Save these recovery codes now.')}</span>{' '}
        {t('mfa.recoveryHint', 'Each code signs you in once if you lose access to the app. They are shown only this one time and are stored only as hashes.')}
      </InlineAlert>
      <ul id="recovery-codes-title" className="grid gap-2 sm:grid-cols-2" aria-label={t('mfa.recoveryCodes', 'Recovery codes')}>
        {codes.map((code) => (
          <li key={code}><code className="block rounded-control bg-surface px-3 py-2 font-mono text-base tracking-[0.12em] text-foreground">{code}</code></li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" icon={<CopyIcon />} onClick={() => void copyText(text).then((ok) => ok && toast.success(t('mfa.codesCopied', 'Recovery codes copied')))}>
          {t('mfa.copyCodes', 'Copy codes')}
        </Button>
        {onDone && <Button variant="dark" size="sm" onClick={onDone}>{t('mfa.savedCodes', 'I have saved them')}</Button>}
      </div>
    </div>
  );
}
