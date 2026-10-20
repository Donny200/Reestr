import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router-dom';
import { ExternalLinkIcon, FlagIcon, PencilIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Badge, StatusPill } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { buttonVariants } from '../../components/ui/buttonVariants';
import { Field } from '../../components/ui/Field';
import { Textarea } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { SkeletonText } from '../../components/ui/Skeleton';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { InlineAlert } from '../functions/InlineAlert';
import type { InformationReport, ReportHistoryEntry, ReportStatus } from '../../types/reports';
import { errorMessage, fieldErrorsOf } from '../../utils/errors';
import { formatDateTime } from '../../utils/format';
import { useReport, useReportHistory, useUpdateReportStatus } from './queries';
import {
  CLOSED_REPORT_STATUSES,
  REPORT_NOTE_MAX,
  REPORT_TRANSITIONS,
  canManageReports,
  reportCategoryLabel,
  reportStatusLabel,
  reportStatusLabels,
  reportStatusTones,
  reportTypeLabel,
} from './reportOptions';

const actionLabels: Record<Exclude<ReportStatus, 'NEW'>, { key: string; fallback: string }> = {
  IN_PROGRESS: { key: 'reports.startHandling', fallback: 'Start handling' },
  RESOLVED: { key: 'reports.resolve', fallback: 'Mark resolved' },
  REJECTED: { key: 'reports.reject', fallback: 'Reject' },
};

const reopenLabel = { key: 'reports.reopen', fallback: 'Reopen' };

function publicPath(report: InformationReport): string {
  return report.entityType === 'FUNCTION' ? `/functions/${report.entityId}` : `/organizations/${report.entityId}`;
}

const TRANSITION = /:\s*([A-Z_]+)\s*->\s*([A-Z_]+)/;

function HistoryEntry({ entry }: { entry: ReportHistoryEntry }) {
  const { t, locale } = useI18n();
  const [first, ...rest] = (entry.details ?? '').split('\n');
  const transition = entry.action === 'REPORT_STATUS_CHANGE' ? first.match(TRANSITION) : null;
  const explanation = rest.join('\n').trim();
  const actor = entry.performedBy === 'public:anonymous' ? t('reports.history.visitor', 'Visitor') : entry.performedBy;
  const statusOf = (value: string) => (value in reportStatusLabels ? reportStatusLabel(value as ReportStatus, t) : value);

  return (
    <li className="relative border-s border-line ps-4">
      <span className="absolute -start-[5px] top-1.5 h-2.5 w-2.5 rounded-pill bg-line-strong" aria-hidden="true" />
      <p className="text-sm font-medium text-foreground">
        {entry.action === 'REPORT_RECEIVED'
          ? t('reports.history.received', 'Report received')
          : transition
            ? `${statusOf(transition[1])} → ${statusOf(transition[2])}`
            : t('reports.history.changed', 'Status changed')}
      </p>
      <p className="mt-0.5 text-xs text-secondary">
        <span className="tabular-nums">{formatDateTime(entry.performedAt, locale)}</span> · <span className="wrap-anywhere">{actor}</span>
      </p>
      {explanation && <p className="mt-1.5 whitespace-pre-wrap rounded-control bg-surface px-3 py-2 text-sm text-foreground wrap-anywhere">{explanation}</p>}
    </li>
  );
}

interface ReportReviewDialogProps {
  report: InformationReport | null;
  onClose: () => void;
}

export function ReportReviewDialog({ report, onClose }: ReportReviewDialogProps) {
  const { t, locale } = useI18n();
  const { hasPermission } = useAuth();
  const id = report?.id ?? null;
  const detail = useReport(id);
  const history = useReportHistory(id);
  const update = useUpdateReportStatus();
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);
  const historyTitleId = useId();
  const resetUpdate = update.reset;
  const current = detail.data ?? report;
  const canManage = canManageReports(hasPermission);
  const serverNoteError = fieldErrorsOf(update.error).note;

  useEffect(() => {
    setNote('');
    setNoteError(null);
    resetUpdate();
  }, [id, resetUpdate]);

  const changeStatus = (target: ReportStatus) => {
    if (!current) return;
    const explanation = note.trim();
    if (target === 'REJECTED' && !explanation) {
      setNoteError(t('reports.rejectNeedsNote', 'Explain why the report is rejected.'));
      return;
    }
    setNoteError(null);
    update.mutate(
      { id: current.id, status: target, note: explanation },
      {
        onSuccess: () => {
          setNote('');
          toast.success(t('toast.updated', 'Saved successfully'));
        },
      },
    );
  };

  const closed = current ? CLOSED_REPORT_STATUSES.includes(current.status) : false;

  return (
    <Modal
      open={report !== null}
      onClose={onClose}
      closeDisabled={update.isPending}
      size="lg"
      icon={<FlagIcon aria-hidden="true" />}
      title={current?.entityLabel ?? ''}
      description={current ? `${reportTypeLabel(current.entityType, t)} #${current.entityId} · ${formatDateTime(current.createdAt, locale)}` : undefined}
    >
      {current && (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusPill tone={reportStatusTones[current.status]}>{reportStatusLabel(current.status, t)}</StatusPill>
            <Badge size="sm" tone="accent">{reportCategoryLabel(current.category, t)}</Badge>
            {current.language && <Badge size="sm">{current.language.toUpperCase()}</Badge>}
          </div>
          <section aria-label={t('reports.description', 'Report')}>
            <p className="micro text-secondary">{t('reports.description', 'Report')}</p>
            <p lang={current.language ?? undefined} className="mt-2 whitespace-pre-wrap rounded-control bg-surface px-4 py-3 text-base leading-7 text-foreground wrap-anywhere">
              {current.description}
            </p>
          </section>
          <div>
            <p className="micro text-secondary">{t('reports.contact', 'Reporter contact')}</p>
            <p className="mt-1 text-sm text-foreground wrap-anywhere">
              {current.contact ?? (
                <span className="text-secondary">
                  {current.contactAvailable
                    ? t('reports.contactHidden', 'The visitor left contact details. Only staff who handle reports can see them.')
                    : closed
                      ? t('reports.contactErased', 'Not stored: contact details are deleted when a report is closed.')
                      : t('reports.noContact', 'The visitor did not leave contact details.')}
                </span>
              )}
            </p>
          </div>
          {current.serviceChangedSinceReport && (
            <InlineAlert tone="info">
              {t('reports.cardChanged', 'The service card was edited after this report arrived. Check whether the edit fixed the problem before you resolve the report.')}
            </InlineAlert>
          )}
          <div className="flex flex-wrap gap-2">
            <Link to={publicPath(current)} target="_blank" rel="noopener noreferrer" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
              <ExternalLinkIcon aria-hidden="true" />
              {t('reports.openPublic', 'Open public page')}
            </Link>
            {current.entityType === 'FUNCTION' && hasPermission('FUNCTIONS_VIEW') && (
              <Link to={`/admin/functions/${current.entityId}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                <PencilIcon aria-hidden="true" />
                {t('reports.openEditor', 'Open in editor')}
              </Link>
            )}
          </div>
          <InlineAlert tone="info">
            {t('reports.noAutoChange', 'Correct the information through the normal editing and review process. Changing the report status does not change the public page.')}
          </InlineAlert>
          {current.resolutionNote && (
            <div>
              <p className="micro text-secondary">{t('reports.latestNote', 'Latest explanation')}</p>
              <p className="mt-1 whitespace-pre-wrap text-sm text-foreground wrap-anywhere">{current.resolutionNote}</p>
            </div>
          )}
          {canManage && (
            <div className="space-y-4 border-t border-line pt-5">
              <Field
                label={t('reports.explanation', 'Explanation')}
                hint={t('reports.explanationHint', 'Required when rejecting a report. Visible to staff only and kept in the history.')}
                error={noteError ?? serverNoteError}
              >
                {(control) => (
                  <Textarea
                    {...control}
                    rows={3}
                    maxLength={REPORT_NOTE_MAX}
                    value={note}
                    disabled={update.isPending}
                    onChange={(event) => {
                      setNote(event.target.value);
                      if (noteError && event.target.value.trim()) setNoteError(null);
                    }}
                  />
                )}
              </Field>
              {Boolean(update.error) && !serverNoteError && <InlineAlert tone="danger">{errorMessage(update.error, t)}</InlineAlert>}
              <div className="flex flex-wrap justify-end gap-2">
                {REPORT_TRANSITIONS[current.status].map((target) => {
                  const label = closed || target === 'NEW' ? reopenLabel : actionLabels[target];
                  return (
                    <Button
                      key={target}
                      variant={target === 'REJECTED' ? 'outline' : 'dark'}
                      size="sm"
                      loading={update.isPending && update.variables?.status === target}
                      disabled={update.isPending}
                      onClick={() => changeStatus(target)}
                    >
                      {t(label.key, label.fallback)}
                    </Button>
                  );
                })}
              </div>
            </div>
          )}
          <section aria-labelledby={historyTitleId} className="border-t border-line pt-5">
            <h3 id={historyTitleId} className="micro text-secondary">{t('reports.history', 'History')}</h3>
            {history.isPending ? (
              <div className="mt-3"><SkeletonText lines={3} /></div>
            ) : history.error ? (
              <InlineAlert tone="danger" className="mt-3">{errorMessage(history.error, t)}</InlineAlert>
            ) : (
              <ol className="mt-3 space-y-4">
                {(history.data ?? []).map((entry) => <HistoryEntry key={entry.id} entry={entry} />)}
              </ol>
            )}
          </section>
        </div>
      )}
    </Modal>
  );
}
