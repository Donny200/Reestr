import { useState } from 'react';
import { Share2Icon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { Input } from '../ui/Input';
import { Modal } from '../ui/Modal';
import { useI18n } from '../../contexts/i18n';
import { shareLink } from '../../utils/share';

interface ShareButtonProps {
  title: string;
  path: string;
}

export function ShareButton({ title, path }: ShareButtonProps) {
  const { t } = useI18n();
  const [manualUrl, setManualUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const share = async () => {
    const url = new URL(path, window.location.origin).toString();
    setBusy(true);
    try {
      const outcome = await shareLink({ title, url });
      if (outcome === 'copied') toast.success(t('share.copied', 'Link copied to the clipboard'));
      if (outcome === 'unavailable') setManualUrl(url);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Button variant="outline" size="sm" icon={<Share2Icon />} loading={busy} onClick={() => void share()} className="print:hidden">
        {t('share.action', 'Share')}
      </Button>
      <Modal
        open={manualUrl !== null}
        onClose={() => setManualUrl(null)}
        size="sm"
        icon={<Share2Icon aria-hidden="true" />}
        title={t('share.manualTitle', 'Copy the link')}
        description={t('share.manualHint', 'Your browser does not allow automatic copying. Select the link and copy it.')}
        footer={<Button variant="dark" onClick={() => setManualUrl(null)}>{t('action.close', 'Close')}</Button>}
      >
        <Field label={t('share.link', 'Link')}>
          {(control) => <Input {...control} readOnly value={manualUrl ?? ''} onFocus={(event) => event.currentTarget.select()} />}
        </Field>
      </Modal>
    </>
  );
}
