import { useEffect, useState } from 'react';
import { CheckIcon, CopyIcon, KeyRoundIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { InlineAlert } from '../functions/InlineAlert';
import { useI18n } from '../../contexts/i18n';
import { useInitiatePasswordReset } from '../legacyUsers/queries';
import type { PasswordResetLink } from '../../types/api';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, fullName } from '../../utils/format';
import { copyText } from '../../utils/share';

interface Props {
  user: { id: number; firstName: string; lastName: string; email: string } | null;
  onClose: () => void;
}

/**
 * SUPER_ADMIN-only reset: generates a single-use link that is displayed exactly once. The user picks the new
 * password through the link; no password is ever shown or generated here.
 */
export function PasswordResetDialog({ user, onClose }: Props) {
  const { t, locale } = useI18n();
  const initiate = useInitiatePasswordReset();
  const [link, setLink] = useState<PasswordResetLink | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    setLink(null);
    setCopied(false);
    setError(null);
  }, [user?.id]);

  const absoluteLink = link ? `${window.location.origin}${link.resetPath}` : '';

  const generate = async () => {
    if (!user) return;
    setError(null);
    try {
      setLink(await initiate.mutateAsync(user.id));
    } catch (failure) {
      setError(failure);
    }
  };

  const copy = async () => {
    if (await copyText(absoluteLink)) {
      setCopied(true);
      toast.success(t('users.linkCopied', 'Link copied'));
    } else {
      toast.error(t('users.copyFailed', 'Could not copy automatically; select the link and copy it manually.'));
    }
  };

  return (
    <Modal
      open={user !== null}
      onClose={onClose}
      closeDisabled={initiate.isPending}
      size="sm"
      icon={<KeyRoundIcon className="h-5 w-5" aria-hidden="true" />}
      title={t('users.resetPassword', 'Reset password')}
      description={user ? `${fullName(user)} · ${user.email}` : undefined}
      footer={
        link ? (
          <Button variant="dark" onClick={onClose}>{t('action.close')}</Button>
        ) : (
          <>
            <Button variant="outline" onClick={onClose} disabled={initiate.isPending}>{t('action.cancel')}</Button>
            <Button variant="dark" icon={<KeyRoundIcon />} loading={initiate.isPending} onClick={() => void generate()}>
              {t('users.generateLink', 'Generate reset link')}
            </Button>
          </>
        )
      }
    >
      <div className="space-y-4">
        {link ? (
          <>
            <InlineAlert tone="warning">
              {t('users.resetLinkHint', 'Copy this link now and send it through an approved channel. It is shown only once, can be used once and expires at')}{' '}
              <span className="font-semibold tabular-nums">{formatDateTime(link.expiresAt, locale)}</span>.
            </InlineAlert>
            <p className="rounded-control bg-surface p-3 font-mono text-xs leading-5 text-foreground wrap-anywhere" aria-label={t('users.resetLinkTitle', 'Password reset link')}>
              {absoluteLink}
            </p>
            <Button variant="outline" size="sm" icon={copied ? <CheckIcon /> : <CopyIcon />} onClick={() => void copy()}>
              {copied ? t('users.linkCopied', 'Link copied') : t('users.copyLink', 'Copy link')}
            </Button>
          </>
        ) : (
          <>
            <p className="text-sm leading-6 text-secondary">
              {t('users.resetPasswordConfirm', 'Generate a single-use password reset link for this user? When the new password is set, every existing session of the user is signed out.')}
            </p>
            <p className="text-sm leading-6 text-secondary">{t('users.resetNeverShowsPassword', 'Passwords are never displayed. The user chooses a new password through the link.')}</p>
            {Boolean(error) && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
          </>
        )}
      </div>
    </Modal>
  );
}
