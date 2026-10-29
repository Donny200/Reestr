import type { ReactNode } from 'react';
import { CheckCircle2Icon, CheckIcon, FlagIcon, MessageSquareWarningIcon } from 'lucide-react';
import { Button } from '../ui/Button';
import { useI18n } from '../../contexts/i18n';
import { formatDateTime } from '../../utils/format';
import { anchorOf } from '../../features/functions/wizard';
import { useReviewFeedback } from '../../features/functions/reviewFeedback';
import type { ReviewComment } from '../../types/adminFunctions';

/** A reviewer's note shown directly above the field it is about; it stays while the author edits the field. */
export function ReviewNote({ comment, label }: { comment: ReviewComment; label: string }) {
  const { t, locale } = useI18n();
  const feedback = useReviewFeedback();
  const reviewer = feedback?.mode === 'reviewer';
  return (
    <div
      className="flex flex-col gap-2 rounded-control border-s-4 border-status-pending bg-status-pending-bg px-4 py-3 text-sm text-foreground sm:flex-row sm:items-start"
      role="note"
    >
      <MessageSquareWarningIcon className="mt-0.5 h-4 w-4 shrink-0 text-status-pending" aria-hidden="true" />
      <div className="min-w-0 flex-1 space-y-1">
        <p className="text-xs font-semibold uppercase tracking-[0.025em] text-status-pending">
          {t('review.feedbackTitle')}
          <span className="sr-only"> {t('review.noteFor', { field: label })}</span>
        </p>
        <p className="whitespace-pre-wrap leading-6 wrap-anywhere">{comment.comment}</p>
        <p className="text-xs text-secondary">
          {t('review.noteMeta', { reviewer: comment.reviewer, date: formatDateTime(comment.createdAt, locale) })}
        </p>
        {comment.addressed && (
          <p className="inline-flex items-center gap-1.5 text-xs font-medium text-status-published">
            <CheckCircle2Icon className="h-3.5 w-3.5" aria-hidden="true" />
            {reviewer ? t('review.noteChangedReviewer') : t('review.noteAddressed')}
          </p>
        )}
      </div>
      {feedback?.onResolve && (
        <Button
          variant="outline"
          size="sm"
          icon={<CheckIcon />}
          disabled={feedback.busy}
          onClick={() => feedback.onResolve?.(comment, label)}
        >
          <span aria-hidden="true">{t('review.resolve')}</span>
          <span className="sr-only">{t('review.resolveField', { field: label })}</span>
        </Button>
      )}
    </div>
  );
}

/**
 * Wraps one reviewable field or item (stable `path`): renders its open review note above it and, for reviewers, a
 * "Flag for correction" button. The wrapper id is the scroll target of the corrections list.
 */
export function Reviewable({ path, label, children, className }: { path: string; label: string; children: ReactNode; className?: string }) {
  const { t } = useI18n();
  const feedback = useReviewFeedback();
  const comment = feedback?.open.get(path);
  const canFlag = feedback?.mode === 'reviewer' && feedback.onFlag && !comment;
  return (
    <div id={anchorOf(path)} data-review-path={path} className={className ? `scroll-mt-28 space-y-2 ${className}` : 'scroll-mt-28 space-y-2'}>
      {comment && <ReviewNote comment={comment} label={label} />}
      {canFlag && (
        <div className="flex justify-end">
          <Button
            variant="ghost"
            size="sm"
            icon={<FlagIcon />}
            disabled={feedback.busy}
            onClick={() => feedback.onFlag?.(path, label)}
          >
            <span aria-hidden="true">{t('review.flag')}</span>
            <span className="sr-only">{t('review.flagField', { field: label })}</span>
          </Button>
        </div>
      )}
      {children}
    </div>
  );
}
