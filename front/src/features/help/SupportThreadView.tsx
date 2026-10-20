import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircleIcon, CheckCheckIcon, CheckIcon, FlagIcon, Loader2Icon, RotateCwIcon, TagIcon } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import type { SupportMessage } from '../../types/help';
import { formatDateTime } from '../../utils/format';
import { reportCategoryLabel } from '../reports/reportOptions';

/** A message the visitor sent that the server has not confirmed yet (or that failed and can be retried). */
export interface PendingMessage {
  clientMessageId: string;
  body: string;
  contextLabel: string | null;
  state: 'sending' | 'failed';
  error?: string;
}

interface SupportThreadViewProps {
  messages: SupportMessage[];
  pending: PendingMessage[];
  organizationName: (id: number | null) => string;
  onRetry: (clientMessageId: string) => void;
}

function sameDay(left: string, right: string) {
  return new Date(left).toDateString() === new Date(right).toDateString();
}

/** One timeline: the visitor on the right, support on the left, the context shown whenever it changes. */
export function SupportThreadView({ messages, pending, organizationName, onRetry }: SupportThreadViewProps) {
  const { t, locale } = useI18n();
  const end = useRef<HTMLDivElement>(null);
  const count = messages.length + pending.length;

  useEffect(() => {
    end.current?.scrollIntoView({ block: 'end', behavior: 'smooth' });
  }, [count]);

  // The context ("About: service X") is shown above a visitor message whenever it differs from their previous one.
  const contextShown = new Set<number>();
  let lastContext: string | undefined;
  for (const message of messages) {
    if (message.author !== 'VISITOR') continue;
    const context = message.contextLabel ?? '';
    if (context !== lastContext) contextShown.add(message.id);
    lastContext = context;
  }

  return (
    <div role="log" aria-live="polite" aria-relevant="additions" aria-label={t('support.thread', 'Your conversation with support')}
      className="flex max-h-[min(65vh,42rem)] min-h-[16rem] flex-col gap-3 overflow-y-auto px-4 py-5 sm:px-6" tabIndex={0}>
      {messages.map((message, index) => {
        const previous = index > 0 ? messages[index - 1] : null;
        const visitor = message.author === 'VISITOR';
        const context = message.contextLabel;
        const showContext = contextShown.has(message.id);
        return (
          <div key={message.id} className="flex flex-col gap-1">
            {(!previous || !sameDay(previous.createdAt, message.createdAt)) && (
              <p className="my-2 text-center text-xs font-medium text-secondary">
                {new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(new Date(message.createdAt))}
              </p>
            )}
            {showContext && (
              <p className={cn('flex items-center gap-1.5 text-xs text-secondary', visitor && 'justify-end')}>
                <TagIcon className="h-3.5 w-3.5" aria-hidden="true" />
                {context ? (
                  message.serviceId ? (
                    <Link to={`/functions/${message.serviceId}`} className="underline-offset-4 fine:hover:underline">{t('support.about', 'About')}: {context}</Link>
                  ) : (
                    <span>{t('support.about', 'About')}: {context}</span>
                  )
                ) : (
                  <span>{t('support.generalContext', 'General support')}</span>
                )}
              </p>
            )}
            <div className={cn('flex', visitor ? 'justify-end' : 'justify-start')}>
              <article
                className={cn('max-w-[min(36rem,85%)] rounded-card-sm px-4 py-3 shadow-sm',
                  visitor ? 'rounded-ee-sm bg-ink text-ink-fg' : 'rounded-es-sm border border-line bg-surface text-foreground')}
                aria-label={visitor ? t('support.youWrote', 'You wrote') : `${t('support.replyFrom', 'Reply from')} ${organizationName(message.organizationId)}`}
              >
                {!visitor && <p className="mb-1 text-xs font-semibold text-accent-text">{organizationName(message.organizationId)}</p>}
                {message.category && (
                  <p className={cn('mb-1 flex items-center gap-1.5 text-xs font-semibold', visitor ? 'text-ink-secondary' : 'text-secondary')}>
                    <FlagIcon className="h-3.5 w-3.5" aria-hidden="true" />
                    {t('support.reportLabel', 'Report of incorrect information')}: {reportCategoryLabel(message.category, t)}
                  </p>
                )}
                <p className="whitespace-pre-line text-base leading-7 wrap-anywhere">{message.body}</p>
                <p className={cn('mt-1 flex items-center justify-end gap-1.5 text-xs tabular-nums', visitor ? 'text-ink-secondary' : 'text-secondary')}>
                  <time dateTime={message.createdAt}>{formatDateTime(message.createdAt, locale)}</time>
                  {visitor && (message.readAt ? (
                    <span className="inline-flex items-center gap-1"><CheckCheckIcon className="h-3.5 w-3.5" aria-hidden="true" />{t('support.seen', 'Seen by support')}</span>
                  ) : (
                    <span className="inline-flex items-center gap-1"><CheckIcon className="h-3.5 w-3.5" aria-hidden="true" />{t('support.sent', 'Sent')}</span>
                  ))}
                </p>
              </article>
            </div>
          </div>
        );
      })}
      {pending.map((message) => (
        <div key={message.clientMessageId} className="flex flex-col items-end gap-1">
          <article className="max-w-[min(36rem,85%)] rounded-card-sm rounded-ee-sm bg-ink/80 px-4 py-3 text-ink-fg" aria-label={t('support.youWrote', 'You wrote')}>
            <p className="whitespace-pre-line text-base leading-7 wrap-anywhere">{message.body}</p>
            <p className="mt-1 flex items-center justify-end gap-1.5 text-xs text-ink-secondary">
              {message.state === 'sending' ? (
                <><Loader2Icon className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" aria-hidden="true" />{t('support.sending', 'Sending…')}</>
              ) : (
                <><AlertCircleIcon className="h-3.5 w-3.5" aria-hidden="true" />{t('support.notSent', 'Not sent')}</>
              )}
            </p>
          </article>
          {message.state === 'failed' && (
            <div className="flex max-w-[min(36rem,85%)] flex-wrap items-center justify-end gap-2 text-sm text-status-danger" role="alert">
              <span>{message.error}</span>
              <Button variant="outline" size="sm" icon={<RotateCwIcon />} onClick={() => onRetry(message.clientMessageId)}>
                {t('support.retry', 'Try again')}
              </Button>
            </div>
          )}
        </div>
      ))}
      <div ref={end} />
    </div>
  );
}
