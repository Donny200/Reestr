import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { KeyRoundIcon, ShieldCheckIcon, ShieldOffIcon } from 'lucide-react';
import { toast } from 'sonner';
import { StatusPill } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { SkeletonText } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { authKeys } from '../queryKeys';
import { InlineAlert } from '../functions/InlineAlert';
import { confirmMfaEnrollment, disableMfa, getMfaStatus, regenerateRecoveryCodes, startMfaEnrollment } from '../../services/authService';
import type { MfaEnrollment } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { formatDate } from '../../utils/format';
import { MfaEnrollmentPanel, RecoveryCodes } from './MfaEnrollmentPanel';

type Dialog = 'disable' | 'regenerate' | null;

/** Two-factor settings of the signed-in user: enroll, see state, regenerate codes or disable with password + code. */
export function MfaSettingsCard() {
  const { t, locale } = useI18n();
  const { refreshUser } = useAuth();
  const client = useQueryClient();
  const status = useQuery({ queryKey: authKeys.mfa(), queryFn: getMfaStatus });
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null);
  const [codes, setCodes] = useState<string[] | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [dialogError, setDialogError] = useState<unknown>(null);

  const refresh = async () => {
    await client.invalidateQueries({ queryKey: authKeys.mfa() });
    await refreshUser();
  };

  const start = useMutation({ mutationFn: startMfaEnrollment, onSuccess: setEnrollment, onError: (error) => toast.error(errorMessage(error, t)) });
  const confirm = useMutation({
    mutationFn: (value: string) => confirmMfaEnrollment(value),
    onSuccess: async (result) => {
      setEnrollment(null);
      setCodes(result.recoveryCodes);
      await refresh();
      toast.success(t('mfa.enabled', 'Two-factor authentication enabled'));
    },
  });
  const disable = useMutation({
    mutationFn: () => disableMfa(password, code.trim()),
    onSuccess: async () => {
      closeDialog();
      await refresh();
      toast.success(t('mfa.disabled', 'Two-factor authentication disabled'));
    },
    onError: setDialogError,
  });
  const regenerate = useMutation({
    mutationFn: () => regenerateRecoveryCodes(password, code.trim()),
    onSuccess: async (result) => {
      closeDialog();
      setCodes(result.recoveryCodes);
      await refresh();
    },
    onError: setDialogError,
  });

  const closeDialog = () => {
    setDialog(null);
    setPassword('');
    setCode('');
    setDialogError(null);
  };
  const submitDialog = (event: FormEvent) => {
    event.preventDefault();
    setDialogError(null);
    if (dialog === 'disable') disable.mutate();
    if (dialog === 'regenerate') regenerate.mutate();
  };

  const data = status.data;
  const running = disable.isPending || regenerate.isPending;

  return (
    <Card id="mfa">
      <CardHeader
        icon={<ShieldCheckIcon />}
        title={t('mfa.title', 'Two-factor authentication')}
        description={t('mfa.subtitle', 'A code from an authenticator app is required at every sign-in in addition to your password.')}
        actions={data ? <StatusPill tone={data.enrolled ? 'published' : data.required ? 'danger' : 'draft'}>{data.enrolled ? t('mfa.stateEnabled', 'Enabled') : t('mfa.stateDisabled', 'Not enabled')}</StatusPill> : undefined}
      />
      <CardBody className="space-y-5">
        {status.isPending ? (
          <SkeletonText lines={3} />
        ) : status.error ? (
          <ErrorState error={status.error} onRetry={() => void status.refetch()} />
        ) : !data?.enabled ? (
          <InlineAlert tone="info">{t('mfa.notConfigured', 'Two-factor authentication is not configured on this server.')}</InlineAlert>
        ) : codes ? (
          <RecoveryCodes codes={codes} onDone={() => setCodes(null)} />
        ) : data.enrolled ? (
          <>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-secondary">{t('mfa.enabledSince', 'Enabled since')}</dt>
                <dd className="mt-1 font-medium tabular-nums text-foreground">{formatDate(data.confirmedAt, locale)}</dd>
              </div>
              <div>
                <dt className="text-secondary">{t('mfa.codesRemaining', 'Unused recovery codes')}</dt>
                <dd className="mt-1 font-medium tabular-nums text-foreground">{data.recoveryCodesRemaining}</dd>
              </div>
            </dl>
            {data.recoveryCodesRemaining <= 2 && <InlineAlert tone="warning">{t('mfa.fewCodes', 'You are running out of recovery codes. Generate a new set and store it safely.')}</InlineAlert>}
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" icon={<KeyRoundIcon />} onClick={() => setDialog('regenerate')}>{t('mfa.regenerate', 'New recovery codes')}</Button>
              <Button variant="danger" icon={<ShieldOffIcon />} onClick={() => setDialog('disable')}>{t('mfa.disable', 'Disable')}</Button>
            </div>
            {data.required && <p className="text-xs leading-5 text-secondary">{t('mfa.requiredNote', 'Your role requires a second factor. If you disable it, you will have to set it up again at your next sign-in.')}</p>}
          </>
        ) : (
          <>
            {data.required && (
              <InlineAlert tone={data.enrollmentOverdue ? 'danger' : 'warning'}>
                {data.enrollmentDeadline && !data.enrollmentOverdue
                  ? `${t('mfa.deadline', 'Your role requires two-factor authentication. Set it up before')} ${formatDate(data.enrollmentDeadline, locale)}.`
                  : t('mfa.requiredNow', 'Your role requires two-factor authentication. You will be asked to set it up at your next sign-in.')}
              </InlineAlert>
            )}
            <MfaEnrollmentPanel enrollment={enrollment} starting={start.isPending} onStart={() => start.mutateAsync().then(() => undefined)} onConfirm={(value) => confirm.mutateAsync(value).then(() => undefined)} confirming={confirm.isPending} />
          </>
        )}
      </CardBody>

      <Modal
        open={dialog !== null}
        onClose={closeDialog}
        closeDisabled={running}
        size="sm"
        icon={dialog === 'disable' ? <ShieldOffIcon aria-hidden="true" /> : <KeyRoundIcon aria-hidden="true" />}
        title={dialog === 'disable' ? t('mfa.disable', 'Disable') : t('mfa.regenerate', 'New recovery codes')}
        description={t('mfa.confirmIdentity', 'Confirm with your password and a current code from the app (or a recovery code).')}
      >
        <form onSubmit={submitDialog} className="space-y-4" noValidate>
          <Field label={t('security.currentPassword')} required>
            {(control) => <Input {...control} type="password" autoComplete="current-password" value={password} disabled={running} onChange={(event) => setPassword(event.target.value)} />}
          </Field>
          <Field label={t('mfa.code', 'Verification code')} required>
            {(control) => <Input {...control} autoComplete="one-time-code" maxLength={16} value={code} disabled={running} onChange={(event) => setCode(event.target.value)} />}
          </Field>
          {Boolean(dialogError) && <InlineAlert tone="danger">{errorMessage(dialogError, t)}</InlineAlert>}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="outline" onClick={closeDialog} disabled={running}>{t('action.cancel')}</Button>
            <Button type="submit" variant={dialog === 'disable' ? 'danger' : 'dark'} loading={running} disabled={!password || code.trim().length < 6}>
              {dialog === 'disable' ? t('mfa.disable', 'Disable') : t('mfa.regenerate', 'New recovery codes')}
            </Button>
          </div>
        </form>
      </Modal>
    </Card>
  );
}
