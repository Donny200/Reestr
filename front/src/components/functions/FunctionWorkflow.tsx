import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { CheckCircle2Icon, PencilIcon, PowerOffIcon, RotateCcwIcon, SendIcon, Trash2Icon, UndoIcon } from 'lucide-react';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { Button } from '../ui/Button';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { Field } from '../ui/Field';
import { Textarea } from '../ui/Input';
import { ConfirmModal, Modal } from '../ui/Modal';
import {
  useCreateFunctionRevision,
  useDeactivateFunction,
  useDiscardFunctionRevision,
  useTransitionFunction,
} from '../../features/functions/queries';
import { REASON_MAX } from '../../features/functions/schema';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { errorMessage } from '../../utils/errors';
import type { AdminFunction, FunctionTransition } from '../../types/adminFunctions';

interface Props {
  record: AdminFunction;
  dirty: boolean;
  busy: boolean;
}

type Dialog = 'reject' | 'deactivate' | 'discard' | null;

export function FunctionWorkflow({ record, dirty, busy }: Props) {
  const { hasPermission } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const transition = useTransitionFunction(record.id);
  const deactivate = useDeactivateFunction(record.id);
  const createRevision = useCreateFunctionRevision(record.id);
  const discardRevision = useDiscardFunctionRevision(record.revisionOfId ?? record.id);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<unknown>(null);
  const running = transition.isPending || deactivate.isPending || createRevision.isPending || discardRevision.isPending;
  const disabled = dirty || busy;

  const canEdit = hasPermission('FUNCTIONS_EDIT');
  const canSubmit = (record.status === 'DRAFT' || record.status === 'DEACTIVATED') && hasPermission('FUNCTIONS_SUBMIT_REVIEW');
  const canWithdraw = record.status === 'PENDING_REVIEW' && hasPermission('FUNCTIONS_SUBMIT_REVIEW');
  const canReview = record.status === 'PENDING_REVIEW' && hasPermission('FUNCTIONS_REVIEW');
  const canPublish = record.status === 'PENDING_REVIEW' && hasPermission('FUNCTIONS_PUBLISH');
  const canDeactivate = record.status === 'PUBLISHED' && hasPermission('FUNCTIONS_DEACTIVATE');
  const canReactivate = record.status === 'DEACTIVATED' && !canSubmit && hasPermission('FUNCTIONS_REACTIVATE');
  const canOpenRevision = record.status === 'PUBLISHED' && canEdit;
  const canDiscard = Boolean(record.revisionOfId) && record.status === 'DRAFT' && canEdit;
  if (!(canSubmit || canWithdraw || canReview || canPublish || canDeactivate || canReactivate || canOpenRevision || canDiscard)) return null;

  async function run(action: FunctionTransition | 'deactivate' | 'discard') {
    if (disabled) return;
    setError(null);
    try {
      if (action === 'deactivate') {
        await deactivate.mutateAsync(undefined);
      } else if (action === 'discard') {
        await discardRevision.mutateAsync(undefined);
        setDialog(null);
        toast.success(t('fnAdmin.revisionDiscarded', 'Draft revision discarded; the published version is unchanged'));
        navigate(`/admin/functions/${record.revisionOfId}`, { replace: true });
        return;
      } else {
        await transition.mutateAsync({ action, reason: action === 'reject' ? reason.trim() : undefined });
      }
      setDialog(null);
      setReason('');
      toast.success(t(action === 'withdraw' ? 'fnAdmin.withdrawn' : 'fnAdmin.saved', action === 'withdraw' ? 'Withdrawn from review' : undefined));
    } catch (failure) {
      setError(failure);
    }
  }

  async function openRevision() {
    if (disabled) return;
    setError(null);
    try {
      const revision = record.revision && record.revision.status === 'DRAFT'
        ? record.revision
        : await createRevision.mutateAsync(undefined);
      toast.success(t('fnAdmin.revisionOpened', 'Draft revision opened; visitors keep seeing the published version'));
      navigate(`/admin/functions/${revision.id}`);
    } catch (failure) {
      setError(failure);
    }
  }

  const openDialog = (next: Exclude<Dialog, null>) => {
    setError(null);
    setDialog(next);
  };

  const submitReject = (event: FormEvent) => {
    event.preventDefault();
    void run('reject');
  };

  const revisionPending = record.revision?.status === 'PENDING_REVIEW';

  return (
    <Card>
      <CardHeader title={t('fnAdmin.workflow', 'Workflow')} />
      <CardBody className="space-y-3">
        <div className="grid gap-2">
          {canOpenRevision && (
            <Button
              variant="dark"
              icon={<PencilIcon />}
              disabled={disabled || revisionPending}
              loading={createRevision.isPending}
              onClick={() => void openRevision()}
            >
              {record.revision ? t('fnAdmin.openRevision', 'Open draft revision') : t('action.edit')}
            </Button>
          )}
          {canSubmit && (
            <Button variant="dark" icon={<SendIcon />} disabled={disabled || record.organizationId === null} loading={transition.isPending} onClick={() => void run('submit-for-review')}>
              {t('fnAdmin.submit')}
            </Button>
          )}
          {canPublish && (
            <Button variant="dark" icon={<CheckCircle2Icon />} disabled={disabled} loading={transition.isPending} onClick={() => void run('publish')}>
              {t('fnAdmin.publish')}
            </Button>
          )}
          {canReview && (
            <Button variant="outline" icon={<UndoIcon />} disabled={disabled} onClick={() => openDialog('reject')}>
              {t('fnAdmin.reject')}
            </Button>
          )}
          {canWithdraw && (
            <Button variant="outline" icon={<RotateCcwIcon />} disabled={disabled} loading={transition.isPending} onClick={() => void run('withdraw')}>
              {t('fnAdmin.withdraw', 'Withdraw from review')}
            </Button>
          )}
          {canReactivate && (
            <Button variant="dark" icon={<RotateCcwIcon />} disabled={disabled} loading={transition.isPending} onClick={() => void run('reactivate')}>
              {t('fnAdmin.reactivate')}
            </Button>
          )}
          {canDiscard && (
            <Button variant="outline" icon={<Trash2Icon />} disabled={disabled} onClick={() => openDialog('discard')}>
              {t('fnAdmin.discardRevision', 'Discard revision')}
            </Button>
          )}
          {canDeactivate && (
            <Button variant="danger" icon={<PowerOffIcon />} disabled={disabled} onClick={() => openDialog('deactivate')}>
              {t('action.deactivate')}
            </Button>
          )}
        </div>
        {canOpenRevision && (
          <p className="text-sm leading-6 text-secondary">
            {revisionPending
              ? t('fnAdmin.revisionPending', 'A revision is pending review; the published version stays visible until it is approved.')
              : t('fnAdmin.editPublishedHint', 'Editing opens a draft revision. Visitors keep seeing the current published version until the revision is reviewed and published.')}
          </p>
        )}
        {canPublish && record.revisionOfId && (
          <p className="text-sm leading-6 text-secondary">{t('fnAdmin.publishRevisionHint', 'Publishing replaces the public version of the service with this revision.')}</p>
        )}
        {dirty && <InlineAlert tone="warning">{t('fnAdmin.saveFirst')}</InlineAlert>}
        {canSubmit && record.organizationId === null && <InlineAlert tone="warning">{t('fnAdmin.assignFirst')}</InlineAlert>}
        {Boolean(error) && dialog !== 'reject' && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
      </CardBody>

      <Modal
        open={dialog === 'reject'}
        onClose={() => setDialog(null)}
        closeDisabled={running}
        title={t('fnAdmin.reject')}
        icon={<UndoIcon aria-hidden="true" />}
      >
        <form className="space-y-5" onSubmit={submitReject}>
          <Field label={t('fnAdmin.reason')} required>
            {(control) => (
              <Textarea {...control} rows={4} maxLength={REASON_MAX} value={reason} disabled={running} onChange={(event) => setReason(event.target.value)} />
            )}
          </Field>
          {Boolean(error) && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="outline" onClick={() => setDialog(null)} disabled={running}>{t('action.cancel')}</Button>
            <Button type="submit" variant="dark" icon={<UndoIcon />} loading={running} disabled={disabled || !reason.trim()}>
              {t('fnAdmin.reject')}
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={dialog === 'deactivate'}
        onClose={() => setDialog(null)}
        onConfirm={() => void run('deactivate')}
        loading={running}
        title={t('action.deactivate')}
        message={t('fnAdmin.deactivateConfirm')}
        confirmLabel={t('action.deactivate')}
        cancelLabel={t('action.cancel')}
      />

      <ConfirmModal
        open={dialog === 'discard'}
        onClose={() => setDialog(null)}
        onConfirm={() => void run('discard')}
        loading={running}
        title={t('fnAdmin.discardRevision', 'Discard revision')}
        message={t('fnAdmin.discardRevisionConfirm', 'Discard every change in this draft revision? The published version stays as it is.')}
        confirmLabel={t('fnAdmin.discardRevision', 'Discard revision')}
        cancelLabel={t('action.cancel')}
      />
    </Card>
  );
}
