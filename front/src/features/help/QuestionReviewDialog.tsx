import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { FlagIcon, MailIcon, MessagesSquareIcon, RotateCwIcon, SendIcon, TagIcon, UserCheckIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Badge, StatusPill } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Field } from '../../components/ui/Field';
import { Textarea } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { Select } from '../../components/ui/Select';
import { SkeletonText } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import { InlineAlert } from '../functions/InlineAlert';
import { reportCategoryLabel } from '../reports/reportOptions';
import type { HelpConversationStatus, HelpConversationSummary } from '../../types/help';
import { errorMessage, fieldErrorsOf } from '../../utils/errors';
import { formatDateTime } from '../../utils/format';
import { HELP_REPLY_PERMISSION, SUPPORT_MESSAGE_MAX, helpStatusLabel, helpStatusTones } from './helpOptions';
import { useHelpAssign, useHelpCandidates, useHelpInboxDetail, useHelpMarkRead, useHelpReply, useHelpStatus } from './queries';
import { newClientMessageId } from './supportActivity';

interface QuestionReviewDialogProps {
  conversation: HelpConversationSummary | null;
  organizationName: (id: number | null | undefined) => string;
  onClose: () => void;
}

/** One routed conversation of a visitor's support thread, as seen and answered by staff. */
export function QuestionReviewDialog({ conversation, organizationName, onClose }: QuestionReviewDialogProps) {
  const { t, locale } = useI18n();
  const { user, hasPermission } = useAuth();
  const id = conversation?.id ?? null;
  const detail = useHelpInboxDetail(id);
  const canReply = hasPermission(HELP_REPLY_PERMISSION);
  const candidates = useHelpCandidates(id, canReply);
  const reply = useHelpReply();
  const status = useHelpStatus();
  const assign = useHelpAssign();
  const { mutate: markRead } = useHelpMarkRead();
  const [text, setText] = useState('');
  const [error, setError] = useState<string>();
  const [failed, setFailed] = useState<{ message: string; clientMessageId: string } | null>(null);
  const lastMarked = useRef(0);

  useEffect(() => {
    setText('');
    setError(undefined);
    setFailed(null);
    lastMarked.current = 0;
  }, [id]);

  // Opening the conversation marks the visitor's messages as read for the whole team (persisted on the server).
  const latestUnread = useMemo(
    () => (detail.data?.messages ?? []).filter((message) => message.author === 'VISITOR' && !message.readAt).reduce((max, message) => Math.max(max, message.id), 0),
    [detail.data],
  );
  useEffect(() => {
    const mark = () => {
      if (id && latestUnread > lastMarked.current && document.visibilityState === 'visible') {
        lastMarked.current = latestUnread;
        markRead({ id, upTo: latestUnread });
      }
    };
    mark();
    document.addEventListener('visibilitychange', mark);
    return () => document.removeEventListener('visibilitychange', mark);
  }, [id, latestUnread, markRead]);

  const current = detail.data?.conversation ?? conversation;
  const busy = reply.isPending || status.isPending || assign.isPending;
  const closed = current?.status === 'CLOSED';

  const send = async (message: string, clientMessageId: string) => {
    if (!id) return;
    setFailed(null);
    try {
      await reply.mutateAsync({ id, message, clientMessageId });
      setText('');
      setError(undefined);
    } catch (failure) {
      setFailed({ message, clientMessageId });
      setError(fieldErrorsOf(failure).message ?? errorMessage(failure, t));
    }
  };

  const submit = () => {
    if (!text.trim()) {
      setError(t('support.replyRequired', 'Write a reply'));
      return;
    }
    void send(text.trim(), newClientMessageId());
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      submit();
    }
  };

  const move = async (target: HelpConversationStatus) => {
    if (!id) return;
    try {
      await status.mutateAsync({ id, status: target });
      toast.success(`${t('support.statusChanged', 'Status changed')}: ${helpStatusLabel(target, t)}`);
    } catch (failure) {
      toast.error(errorMessage(failure, t));
    }
  };

  const chooseAssignee = async (value: string) => {
    if (!id) return;
    try {
      await assign.mutateAsync({ id, userId: value ? Number(value) : null });
      toast.success(t('support.assigneeSaved', 'Responsible staff member updated'));
    } catch (failure) {
      toast.error(fieldErrorsOf(failure).userId ?? errorMessage(failure, t));
    }
  };

  const subject = current ? (current.general ? t('support.generalContext', 'General support') : current.serviceName ?? organizationName(current.organizationId)) : '';

  return (
    <Modal
      open={conversation !== null}
      onClose={onClose}
      closeDisabled={busy}
      size="lg"
      icon={<MessagesSquareIcon aria-hidden="true" />}
      title={subject}
      description={current ? `#${current.id} · ${current.general ? t('support.centralTeam', 'Central support') : organizationName(current.organizationId)}` : undefined}
      footer={<Button variant="outline" onClick={onClose} disabled={busy}>{t('action.close', 'Close')}</Button>}
    >
      {detail.isPending ? (
        <SkeletonText lines={6} />
      ) : detail.error ? (
        <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
      ) : detail.data && current ? (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusPill tone={helpStatusTones[current.status]}>{helpStatusLabel(current.status, t)}</StatusPill>
            {current.serviceId && (
              <Link to={`/functions/${current.serviceId}`} target="_blank" className="text-sm font-medium text-accent-text underline-offset-4 fine:hover:underline">
                {t('support.openServicePage', 'Open the public service page')}
              </Link>
            )}
            {current.language && <Badge size="sm">{current.language.toUpperCase()}</Badge>}
          </div>

          {detail.data.contact?.email ? (
            <p className="flex items-center gap-2 rounded-control bg-surface px-3 py-2 text-sm text-foreground">
              <MailIcon className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{t('support.visitorContact', 'Visitor contact (for support follow-up only)')}: <a className="font-medium underline-offset-4 fine:hover:underline" href={`mailto:${detail.data.contact.email}`}>{detail.data.contact.email}</a></span>
            </p>
          ) : current.contactAvailable ? (
            <p className="text-sm text-secondary">{t('support.contactHidden', 'The visitor left contact details; staff who can reply see them.')}</p>
          ) : null}

          <InlineAlert tone="info">
            {t('support.staffNotice', 'The visitor sees replies signed by your organization, not your name. Never ask for passwords, identity numbers, payment details or documents.')}
          </InlineAlert>

          <ol className="max-h-[24rem] space-y-3 overflow-y-auto pe-1" aria-label={t('support.messages', 'Messages')}>
            {detail.data.messages.map((message) => (
              <li key={message.id} className={cn('rounded-card-sm border p-4', message.author === 'STAFF' ? 'border-accent/40 bg-surface sm:ms-10' : 'border-line sm:me-10')}>
                <p className="flex flex-wrap items-baseline justify-between gap-2 text-xs text-secondary">
                  <span className="font-semibold text-foreground">
                    {message.author === 'STAFF' ? `${t('support.staffReply', 'Reply')} · ${message.staffLabel ?? ''}` : t('support.visitor', 'Visitor')}
                  </span>
                  <span className="flex items-center gap-2 tabular-nums">
                    <time dateTime={message.createdAt}>{formatDateTime(message.createdAt, locale)}</time>
                    {message.author === 'STAFF' && <span>{message.readAt ? t('support.readByVisitor', 'Read by visitor') : t('support.notReadYet', 'Not read yet')}</span>}
                  </span>
                </p>
                {message.contextLabel && (
                  <p className="mt-1 flex items-center gap-1.5 text-xs text-secondary"><TagIcon className="h-3.5 w-3.5" aria-hidden="true" />{t('support.about', 'About')}: {message.contextLabel}</p>
                )}
                {message.category && (
                  <p className="mt-1 flex items-center gap-1.5 text-xs font-semibold text-status-pending">
                    <FlagIcon className="h-3.5 w-3.5" aria-hidden="true" />
                    {t('support.reportLabel', 'Report of incorrect information')}: {reportCategoryLabel(message.category, t)}
                    {message.reportId && <Link to={`/admin/reports?entityId=${message.serviceId ?? ''}`} className="font-normal underline-offset-4 fine:hover:underline">#{message.reportId}</Link>}
                  </p>
                )}
                <p className="mt-2 whitespace-pre-line text-sm leading-6 text-foreground wrap-anywhere">{message.body}</p>
              </li>
            ))}
          </ol>

          {canReply && (
            <div className="space-y-4 border-t border-line pt-5">
              <Field label={t('support.assignee', 'Responsible staff member')}>
                {(control) => (
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Select
                      {...control}
                      size="sm"
                      disabled={busy || candidates.isPending}
                      value={current.assignedUserId ? String(current.assignedUserId) : ''}
                      onValueChange={(value) => void chooseAssignee(value)}
                      options={[
                        { value: '', label: t('support.unassigned', 'Nobody assigned') },
                        ...(candidates.data ?? []).map((member) => ({
                          value: String(member.id),
                          label: [member.firstName, member.lastName].filter(Boolean).join(' ') || member.email,
                        })),
                        ...(current.assignedUserId && !(candidates.data ?? []).some((member) => member.id === current.assignedUserId)
                          ? [{ value: String(current.assignedUserId), label: current.assignedUserName ?? `#${current.assignedUserId}` }]
                          : []),
                      ]}
                    />
                    {user && current.assignedUserId !== user.id && (
                      <Button variant="outline" size="sm" icon={<UserCheckIcon />} disabled={busy} onClick={() => void chooseAssignee(String(user.id))}>
                        {t('support.assignMe', 'Assign to me')}
                      </Button>
                    )}
                  </div>
                )}
              </Field>

              {closed ? (
                <InlineAlert tone="info" action={<Button variant="outline" size="sm" disabled={busy} onClick={() => void move('NEW')}>{t('support.reopen', 'Reopen')}</Button>}>
                  {t('support.closedNotice', 'This conversation is closed. Reopen it to reply. A new visitor message reopens it automatically.')}
                </InlineAlert>
              ) : (
                <>
                  <Field label={t('support.reply', 'Reply to the visitor')} error={error} hint={t('support.replyHint', 'Ctrl+Enter sends. Answer only with information you can confirm.')}>
                    {(control) => <Textarea {...control} rows={4} maxLength={SUPPORT_MESSAGE_MAX} value={text} onKeyDown={onKeyDown} onChange={(event) => setText(event.target.value)} />}
                  </Field>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="dark" size="sm" icon={<SendIcon />} loading={reply.isPending} disabled={busy && !reply.isPending} onClick={submit}>
                      {t('support.sendReply', 'Send reply')}
                    </Button>
                    {failed && (
                      <Button variant="outline" size="sm" icon={<RotateCwIcon />} disabled={busy} onClick={() => void send(failed.message, failed.clientMessageId)}>
                        {t('support.retry', 'Try again')}
                      </Button>
                    )}
                    {current.status !== 'NEW' && (
                      <Button variant="ghost" size="sm" disabled={busy} onClick={() => void move('NEW')}>{t('support.markOpen', 'Mark open')}</Button>
                    )}
                    {current.status !== 'IN_PROGRESS' && (
                      <Button variant="outline" size="sm" disabled={busy} onClick={() => void move('IN_PROGRESS')}>{t('support.markInProgress', 'Mark in progress')}</Button>
                    )}
                    <Button variant="ghost" size="sm" disabled={busy} onClick={() => void move('CLOSED')}>{t('support.close', 'Close conversation')}</Button>
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      ) : null}
    </Modal>
  );
}
