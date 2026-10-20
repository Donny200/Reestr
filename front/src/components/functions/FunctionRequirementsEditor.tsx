import { useEffect, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { useI18n } from '../../contexts/i18n';
import { useUpdateFunctionRequirements } from '../../features/functions/queries';
import { REQUIREMENTS_MAX } from '../../features/functions/schema';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { useUnsavedChanges } from '../../hooks/useUnsavedChanges';
import { errorMessage } from '../../utils/errors';
import { StatusPill } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { Field } from '../ui/Field';
import { Textarea } from '../ui/Input';
import type { AdminFunction } from '../../types/adminFunctions';

interface Props {
  record: AdminFunction;
  busy: boolean;
  onDirty: (dirty: boolean) => void;
}

export function FunctionRequirementsEditor({ record, busy, onDirty }: Props) {
  const { t } = useI18n();
  const update = useUpdateFunctionRequirements(record.id);
  const baseline = record.requirements ?? '';
  const [text, setText] = useState(baseline);
  const [error, setError] = useState<unknown>(null);
  const dirty = text !== baseline;

  useEffect(() => {
    setText(baseline);
    setError(null);
  }, [baseline]);

  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);

  useUnsavedChanges(dirty, t('fnAdmin.discard'));

  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy || !dirty || !text.trim()) return;
    setError(null);
    try {
      await update.mutateAsync(text);
      toast.success(t('fnAdmin.saved'));
    } catch (failure) {
      setError(failure);
    }
  }

  return (
    <Card>
      <CardHeader title={t('fnEdit.title')} description={t('fnAdmin.requirementsHint')} />
      <CardBody>
        <form className="space-y-4" onSubmit={save}>
          <Field label={t('field.requirements')} required hint={t('fnEdit.hint')}>
            {(control) => (
              <Textarea {...control} rows={6} maxLength={REQUIREMENTS_MAX} value={text} disabled={busy} onChange={(event) => setText(event.target.value)} />
            )}
          </Field>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="text-xs tabular-nums text-secondary">{text.length} / {REQUIREMENTS_MAX}</span>
            {dirty && <StatusPill tone="pending">{t('fnAdmin.unsaved')}</StatusPill>}
          </div>
          {Boolean(error) && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
          <div className="flex justify-end">
            <Button type="submit" variant="dark" loading={update.isPending} disabled={busy || !dirty || !text.trim()}>
              {t('action.save')}
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}
