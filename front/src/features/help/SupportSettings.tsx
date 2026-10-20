import { useState, type FormEvent } from 'react';
import { KeyRoundIcon, MailIcon, MonitorSmartphoneIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Card, CardBody } from '../../components/ui/Card';
import { Field } from '../../components/ui/Field';
import { Checkbox, Input } from '../../components/ui/Input';
import { ConfirmModal } from '../../components/ui/Modal';
import { useI18n } from '../../contexts/i18n';
import type { SupportThread } from '../../types/help';
import { errorMessage, fieldErrorsOf } from '../../utils/errors';
import { copyText } from '../../utils/share';
import { HELP_EMAIL_PATTERN } from './helpOptions';
import { useSupportSettings } from './queries';
import { forgetSupportActive } from './supportActivity';

/** How the visitor keeps access to the thread, optional contact details and (if configured) e-mail notices. */
export function SupportSettings({ thread }: { thread: SupportThread }) {
  const { t } = useI18n();
  const settings = useSupportSettings();
  const [code, setCode] = useState<string | null>(null);
  const [restore, setRestore] = useState('');
  const [restoreError, setRestoreError] = useState<string>();
  const [email, setEmail] = useState(thread.contact?.email ?? '');
  const [notify, setNotify] = useState(thread.contact?.notifyByEmail ?? false);
  const [emailError, setEmailError] = useState<string>();
  const [confirmForget, setConfirmForget] = useState(false);
  const hasThread = thread.identity !== 'NONE';

  const createCode = async () => {
    try {
      setCode((await settings.recoveryCode.mutateAsync()).code);
    } catch (error) {
      toast.error(errorMessage(error, t));
    }
  };

  const submitRestore = async (event: FormEvent) => {
    event.preventDefault();
    setRestoreError(undefined);
    try {
      await settings.recover.mutateAsync(restore);
      setRestore('');
      toast.success(t('support.restored', 'Your support conversation is open in this browser'));
    } catch (error) {
      setRestoreError(fieldErrorsOf(error, t).code ?? errorMessage(error, t));
    }
  };

  const saveContact = async (event: FormEvent) => {
    event.preventDefault();
    const value = email.trim();
    if (value && !HELP_EMAIL_PATTERN.test(value)) {
      setEmailError(t('support.emailInvalid', 'Enter a valid e-mail address or leave the field empty'));
      return;
    }
    setEmailError(undefined);
    try {
      const saved = await settings.contact.mutateAsync({ email: value || null, notifyByEmail: Boolean(value) && notify });
      setEmail(saved.email ?? '');
      setNotify(saved.notifyByEmail);
      toast.success(value ? t('support.contactSaved', 'Contact details saved') : t('support.contactRemoved', 'Contact details removed'));
    } catch (error) {
      const fields = fieldErrorsOf(error, t);
      setEmailError(fields.email ?? fields.notifyByEmail ?? errorMessage(error, t));
    }
  };

  const forget = async () => {
    try {
      await settings.forget.mutateAsync();
      forgetSupportActive();
      setConfirmForget(false);
      toast.success(t('support.forgotten', 'This browser no longer has access to the conversation'));
    } catch (error) {
      toast.error(errorMessage(error, t));
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardBody className="space-y-3 p-5 text-sm leading-6 text-secondary">
          <h2 className="flex items-center gap-2 text-base font-semibold text-foreground">
            <MonitorSmartphoneIcon className="h-4 w-4" aria-hidden="true" />
            {t('support.accessTitle', 'Keeping access to your conversation')}
          </h2>
          {thread.identity === 'ACCOUNT' ? (
            <p>{t('support.accountAccess', 'You are signed in, so this conversation belongs to your account. Sign in on another device to see it there.')}</p>
          ) : (
            <>
              <p>{t('support.browserAccess', 'You do not need an account. This browser remembers your conversation with a private, secure cookie. If you clear browser data, use a private window or switch devices, you can lose access to it.')}</p>
              {hasThread && (
                <div className="space-y-2 rounded-control bg-surface p-3">
                  <p className="font-medium text-foreground">{t('support.recoveryTitle', 'Recovery code (optional)')}</p>
                  <p>{t('support.recoveryText', 'Save a recovery code to open this conversation in another browser or after clearing data. Anyone with the code can read the conversation; it is shown only once.')}</p>
                  {code ? (
                    <div className="space-y-2" role="status">
                      <p className="rounded-control border border-line bg-background px-3 py-2 font-mono text-base tracking-wider text-foreground">{code}</p>
                      <Button variant="outline" size="sm" onClick={() => void copyText(code).then((ok) => ok && toast.success(t('support.codeCopied', 'Recovery code copied')))}>
                        {t('support.copyCode', 'Copy code')}
                      </Button>
                    </div>
                  ) : (
                    <Button variant="outline" size="sm" icon={<KeyRoundIcon />} loading={settings.recoveryCode.isPending} onClick={() => void createCode()}>
                      {thread.recoveryCodeSet ? t('support.newCode', 'Create a new code (the old one stops working)') : t('support.createCode', 'Create a recovery code')}
                    </Button>
                  )}
                </div>
              )}
              <form onSubmit={(event) => void submitRestore(event)} noValidate className="space-y-2">
                <Field label={t('support.restoreLabel', 'Have a recovery code?')} error={restoreError}>
                  {(control) => <Input {...control} autoComplete="off" value={restore} placeholder="XXXXX-XXXXX-XXXXX-XXXXX" onChange={(event) => setRestore(event.target.value)} />}
                </Field>
                <Button type="submit" variant="outline" size="sm" loading={settings.recover.isPending} disabled={!restore.trim()}>
                  {t('support.restore', 'Open my conversation here')}
                </Button>
              </form>
              {hasThread && (
                <Button variant="ghost" size="sm" onClick={() => setConfirmForget(true)}>{t('support.forget', 'Forget this browser')}</Button>
              )}
            </>
          )}
        </CardBody>
      </Card>

      {hasThread && (
        <Card>
          <CardBody className="p-5">
            <form onSubmit={(event) => void saveContact(event)} noValidate className="space-y-3 text-sm leading-6 text-secondary">
              <h2 className="flex items-center gap-2 text-base font-semibold text-foreground">
                <MailIcon className="h-4 w-4" aria-hidden="true" />
                {t('support.contactTitle', 'Contact details (optional)')}
              </h2>
              <p>{t('support.contactText', 'Not required. Support staff handling your conversation can see it and use it only for follow-up. Remove it at any time.')}</p>
              <Field label={t('support.email', 'E-mail')} error={emailError}>
                {(control) => <Input {...control} type="email" autoComplete="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} />}
              </Field>
              {thread.notificationsAvailable ? (
                <label className="flex items-start gap-2 text-foreground">
                  <Checkbox className="mt-1" checked={notify} onChange={(event) => setNotify(event.target.checked)} disabled={!email.trim()} />
                  <span>{t('support.notifyOptIn', 'E-mail me when support replies (the e-mail contains no message text)')}</span>
                </label>
              ) : (
                <p>{t('support.noEmailNotices', 'E-mail notices are not set up on this site. You will see replies here and in the Support badge while you visit the site.')}</p>
              )}
              <Button type="submit" variant="outline" size="sm" loading={settings.contact.isPending}>{t('action.save', 'Save')}</Button>
            </form>
          </CardBody>
        </Card>
      )}

      <ConfirmModal
        open={confirmForget}
        title={t('support.forget', 'Forget this browser')}
        message={t('support.forgetConfirm', 'This browser will no longer open your support conversation. Support staff keep it. Without a recovery code you cannot open it again.')}
        confirmLabel={t('support.forget', 'Forget this browser')}
        cancelLabel={t('action.cancel', 'Cancel')}
        loading={settings.forget.isPending}
        onConfirm={() => void forget()}
        onClose={() => setConfirmForget(false)}
      />
    </div>
  );
}
