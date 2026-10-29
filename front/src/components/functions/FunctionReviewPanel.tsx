import { useState, type FormEvent } from 'react';
import { CheckCircle2Icon, CircleIcon, FlagIcon, MessageSquareWarningIcon } from 'lucide-react';
import { Button } from '../ui/Button';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { Field } from '../ui/Field';
import { Textarea } from '../ui/Input';
import { Modal } from '../ui/Modal';
import { StatusPill } from '../ui/Badge';
import { useI18n } from '../../contexts/i18n';
import { InlineAlert } from '../../features/functions/InlineAlert';
import { REASON_MAX } from '../../features/functions/schema';
import { SECTION_PREFIX, WIZARD_STEPS } from '../../features/functions/wizard';
import { errorMessage } from '../../utils/errors';
import { formatDateTime } from '../../utils/format';
import type { FunctionReview, ReviewComment } from '../../types/adminFunctions';

interface PanelProps {
  review: FunctionReview;
  mode: 'author' | 'reviewer';
  labelOf: (path: string) => string;
  onNavigate: (path: string) => void;
}

/**
 * Review summary beside the editor: the reviewer's checklist, every outstanding correction (each one jumps to its
 * field) and the earlier feedback, which is kept after it is resolved.
 */
export function FunctionReviewPanel({ review, mode, labelOf, onNavigate }: PanelProps) {
  const { t, locale } = useI18n();
  const open = review.comments.filter((comment) => comment.status === 'OPEN');
  const resolved = review.comments.filter((comment) => comment.status === 'RESOLVED').reverse();
  const reviewed = new Set(review.reviewedSections.map((mark) => mark.section));
  const addressed = open.filter((comment) => comment.addressed).length;
  if (mode === 'author' && review.comments.length === 0) return null;

  return (
    <Card id="function-review">
      <CardHeader
        title={mode === 'reviewer' ? t('review.title') : t('review.feedbackTitle')}
        description={review.reviewCycle > 0 ? t('review.cycle', { cycle: review.reviewCycle }) : undefined}
        icon={<MessageSquareWarningIcon />}
      />
      <CardBody className="space-y-5">
        {mode === 'reviewer' && (
          <section className="space-y-2" aria-labelledby="review-checklist">
            <h3 id="review-checklist" className="text-sm font-semibold text-foreground">
              {t('review.sectionsReviewed', { count: reviewed.size, total: WIZARD_STEPS.length })}
            </h3>
            <ul className="space-y-1">
              {WIZARD_STEPS.map((step) => (
                <li key={step}>
                  <button
                    type="button"
                    className="flex w-full items-center gap-2 rounded-control px-2 py-1.5 text-start text-sm text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-ink fine:hover:bg-surface"
                    onClick={() => onNavigate(`${SECTION_PREFIX}${step}`)}
                  >
                    {reviewed.has(step)
                      ? <CheckCircle2Icon className="h-4 w-4 shrink-0 text-status-published" aria-hidden="true" />
                      : <CircleIcon className="h-4 w-4 shrink-0 text-secondary" aria-hidden="true" />}
                    <span className="min-w-0 flex-1">{t(`wizard.step.${step}`)}</span>
                    <span className="sr-only">{reviewed.has(step) ? t('review.reviewed') : t('review.notReviewed')}</span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}

        <section className="space-y-2" aria-labelledby="review-outstanding">
          <h3 id="review-outstanding" className="text-sm font-semibold text-foreground">
            {t('review.outstanding')} <span className="font-normal text-secondary">({open.length})</span>
          </h3>
          {open.length === 0 ? (
            <p className="text-sm text-secondary">{t('review.noCorrections')}</p>
          ) : (
            <ul className="space-y-2">
              {open.map((comment) => (
                <li key={comment.id}>
                  <button
                    type="button"
                    className="w-full space-y-1 rounded-control border border-line px-3 py-2 text-start text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-ink fine:hover:bg-surface"
                    onClick={() => onNavigate(comment.fieldPath)}
                  >
                    <span className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-medium text-foreground wrap-anywhere">{labelOf(comment.fieldPath)}</span>
                      <StatusPill tone={comment.addressed ? 'published' : 'pending'}>
                        {comment.addressed ? t('review.changedShort') : t('review.needsChange')}
                      </StatusPill>
                    </span>
                    <span className="line-clamp-2 block text-secondary wrap-anywhere">{comment.comment}</span>
                    <span className="sr-only">{t('review.goToField')}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {mode === 'author' && open.length > 0 && (
            <InlineAlert tone={addressed === open.length ? 'success' : 'warning'}>
              {addressed === open.length
                ? t('review.readyToResubmit')
                : t('review.resubmitWhen', { done: addressed, total: open.length })}
            </InlineAlert>
          )}
        </section>

        {resolved.length > 0 && (
          <details className="group">
            <summary className="cursor-pointer text-sm font-semibold text-foreground">
              {t('review.history')} <span className="font-normal text-secondary">({resolved.length})</span>
            </summary>
            <ol className="mt-3 space-y-3">
              {resolved.map((comment) => (
                <li key={comment.id} className="space-y-1 border-s-2 border-line ps-3 text-sm">
                  <p className="font-medium text-foreground wrap-anywhere">{labelOf(comment.fieldPath)}</p>
                  <p className="whitespace-pre-wrap text-secondary wrap-anywhere">{comment.comment}</p>
                  <p className="text-xs text-secondary">
                    {t('review.historyMeta', { cycle: comment.reviewCycle, reviewer: comment.reviewer, date: formatDateTime(comment.createdAt, locale) })}
                  </p>
                  {comment.resolution && (
                    <p className="text-xs text-secondary">
                      {t(`review.resolution.${comment.resolution}`)}
                      {comment.resolvedAt ? ` · ${formatDateTime(comment.resolvedAt, locale)}` : ''}
                    </p>
                  )}
                </li>
              ))}
            </ol>
          </details>
        )}
      </CardBody>
    </Card>
  );
}

interface FlagProps {
  open: boolean;
  label: string;
  loading: boolean;
  error: unknown;
  onClose: () => void;
  onSubmit: (comment: string) => void;
}

/** Asks the reviewer for the required explanation of a correction before it is attached to the field. */
export function ReviewFlagDialog({ open, label, loading, error, onClose, onSubmit }: FlagProps) {
  const { t } = useI18n();
  const [comment, setComment] = useState('');
  const [invalid, setInvalid] = useState(false);

  const close = () => {
    setComment('');
    setInvalid(false);
    onClose();
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!comment.trim()) {
      setInvalid(true);
      return;
    }
    onSubmit(comment.trim());
  };

  return (
    <Modal open={open} onClose={close} closeDisabled={loading} title={t('review.flagDialogTitle')} description={label} icon={<FlagIcon aria-hidden="true" />}>
      <form className="space-y-5" onSubmit={submit} noValidate>
        <Field
          label={t('review.explanation')}
          required
          hint={t('review.explanationHint')}
          error={invalid && !comment.trim() ? t('review.explanationRequired') : undefined}
        >
          {(control) => (
            <Textarea
              {...control}
              rows={5}
              maxLength={REASON_MAX}
              value={comment}
              disabled={loading}
              onChange={(event) => setComment(event.target.value)}
            />
          )}
        </Field>
        {Boolean(error) && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={close} disabled={loading}>{t('action.cancel')}</Button>
          <Button type="submit" variant="dark" icon={<FlagIcon />} loading={loading}>{t('review.addCorrection')}</Button>
        </div>
      </form>
    </Modal>
  );
}

/** "Request corrections" confirmation with an optional overall note. */
export function RequestChangesDialog({ open, comments, labelOf, loading, error, onClose, onSubmit }: {
  open: boolean; comments: ReviewComment[]; labelOf: (path: string) => string; loading: boolean; error: unknown;
  onClose: () => void; onSubmit: (summary: string) => void;
}) {
  const { t } = useI18n();
  const [summary, setSummary] = useState('');
  return (
    <Modal open={open} onClose={onClose} closeDisabled={loading} title={t('review.requestChanges')} description={t('review.requestChangesHint')}>
      <form className="space-y-5" onSubmit={(event) => { event.preventDefault(); onSubmit(summary.trim()); }}>
        <div>
          <p className="text-sm font-medium text-foreground">{t('review.openCorrections', { count: comments.length })}</p>
          <ul className="mt-2 list-disc space-y-1 ps-5 text-sm text-secondary">
            {comments.map((comment) => <li key={comment.id} className="wrap-anywhere">{labelOf(comment.fieldPath)}</li>)}
          </ul>
        </div>
        <Field label={t('review.requestChangesSummary')}>
          {(control) => (
            <Textarea {...control} rows={3} maxLength={REASON_MAX} value={summary} disabled={loading} onChange={(event) => setSummary(event.target.value)} />
          )}
        </Field>
        {Boolean(error) && <InlineAlert tone="danger">{errorMessage(error, t)}</InlineAlert>}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onClose} disabled={loading}>{t('action.cancel')}</Button>
          <Button type="submit" variant="dark" loading={loading}>{t('review.requestChanges')}</Button>
        </div>
      </form>
    </Modal>
  );
}
