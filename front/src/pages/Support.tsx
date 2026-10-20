import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { LifeBuoyIcon, SendIcon, XIcon } from 'lucide-react';
import { SectionHeading } from '../components/home/SectionHeading';
import { Button } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { Field } from '../components/ui/Field';
import { Checkbox, Textarea } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { SkeletonText } from '../components/ui/Skeleton';
import { ErrorState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { parseId } from '../features/analytics/scope';
import { usePublicFunctions } from '../features/functions/queries';
import { LONG_NUMBER_PATTERN, REPORT_MESSAGE_MIN, SUPPORT_MESSAGE_MAX } from '../features/help/helpOptions';
import { LiveStatus } from '../features/help/LiveStatus';
import { THREAD_POLL_MS } from '../features/help/liveSupport';
import { useMarkSupportRead, useSendSupportMessage, useSupportThread } from '../features/help/queries';
import { SensitiveDataNotice } from '../features/help/SensitiveDataNotice';
import { ServiceAnswerPreview } from '../features/help/ServiceAnswerPreview';
import { SupportSettings } from '../features/help/SupportSettings';
import { SupportThreadView, type PendingMessage } from '../features/help/SupportThreadView';
import { newClientMessageId } from '../features/help/supportActivity';
import { usePublicOrganizations } from '../features/organizations/queries';
import { REPORT_CATEGORIES, reportCategoryLabel } from '../features/reports/reportOptions';
import type { SendSupportMessage } from '../types/help';
import type { ReportCategory } from '../types/reports';
import { errorMessage, fieldErrorsOf, statusOf, type Translate } from '../utils/errors';
import { localizedText } from '../utils/translations';
import { usePatchSearchParams } from '../hooks/usePatchSearchParams';

function failureText(error: unknown, t: Translate): string {
  const fields = fieldErrorsOf(error);
  if (fields.body) return fields.body;
  if (fields.reportCategory) return fields.reportCategory;
  if (statusOf(error) === 429) return t('support.rateLimited', 'Too many messages in a short time. Please wait a few minutes and try again.');
  if (statusOf(error) === 409) return errorMessage(error, t);
  if (statusOf(error) === 404) return t('support.contextGone', 'This service is no longer published. Choose another subject.');
  return errorMessage(error, t);
}

/**
 * The visitor's private support thread: one conversation over time, with or without an account. Messages about a
 * service reach that service's organization; general messages reach central support. Replies arrive through the live
 * support stream (liveSupport.ts); without it the page checks every few seconds while it is open.
 */
export function Support() {
  const { t, locale } = useI18n();
  const [params, patchParams] = usePatchSearchParams();
  const thread = useSupportThread();
  const send = useSendSupportMessage();
  const markRead = useMarkSupportRead();
  const functions = usePublicFunctions();
  const organizations = usePublicOrganizations();
  const messageId = useId();
  const [text, setText] = useState('');
  const [website, setWebsite] = useState('');
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState<(PendingMessage & { request: SendSupportMessage })[]>([]);

  const serviceId = parseId(params.get('service'));
  const organizationId = serviceId ? undefined : parseId(params.get('organization'));
  const reportMode = params.get('report') === '1' && Boolean(serviceId || organizationId);
  const [category, setCategory] = useState<ReportCategory | ''>('');

  const services = useMemo(
    () => (functions.data ?? [])
      .map((item) => ({ item, name: localizedText(item.name, item.nameTranslations, locale) }))
      .sort((a, b) => a.name.localeCompare(b.name, locale)),
    [functions.data, locale],
  );
  const service = services.find(({ item }) => item.id === serviceId);
  const organization = organizations.data?.find((item) => item.id === (service?.item.organizationId ?? organizationId));
  const organizationLabel = (id: number | null) => {
    if (id === null) return t('support.centralTeam', 'Central support');
    const found = organizations.data?.find((item) => item.id === id);
    return found ? localizedText(found.name, found.nameTranslations, locale) : t('support.organizationTeam', 'Organization support');
  };
  const contextLabel = service?.name ?? (organizationId ? organizationLabel(organizationId) : null);

  const setContext = (value: string) => {
    patchParams({ organization: '', report: '', service: value });
    setCategory('');
  };
  const setReport = (on: boolean) => {
    patchParams({ report: on ? '1' : '' });
    setCategory('');
  };

  // Viewing the page marks staff replies as read (persisted on the server), but only while the tab is visible.
  const latestUnreadReply = useMemo(
    () => (thread.data?.messages ?? []).filter((message) => message.author === 'STAFF' && !message.readAt).reduce((max, message) => Math.max(max, message.id), 0),
    [thread.data],
  );
  const { mutate: markReadUpTo } = markRead;
  const lastMarked = useRef(0);
  useEffect(() => {
    if (!latestUnreadReply) return undefined;
    const mark = () => {
      if (document.visibilityState === 'visible' && lastMarked.current < latestUnreadReply) {
        lastMarked.current = latestUnreadReply;
        markReadUpTo(latestUnreadReply);
      }
    };
    mark();
    document.addEventListener('visibilitychange', mark);
    return () => document.removeEventListener('visibilitychange', mark);
  }, [latestUnreadReply, markReadUpTo]);

  // Server-confirmed messages replace their optimistic copies.
  useEffect(() => {
    const confirmed = new Set((thread.data?.messages ?? []).map((message) => message.clientMessageId).filter(Boolean));
    setPending((current) => current.filter((item) => !confirmed.has(item.clientMessageId)));
  }, [thread.data]);

  const deliver = async (request: SendSupportMessage) => {
    setPending((current) => current.map((item) => (item.clientMessageId === request.clientMessageId ? { ...item, state: 'sending', error: undefined } : item)));
    try {
      await send.mutateAsync(request);
      setPending((current) => current.filter((item) => item.clientMessageId !== request.clientMessageId));
    } catch (failure) {
      setPending((current) => current.map((item) => (item.clientMessageId === request.clientMessageId
        ? { ...item, state: 'failed', error: failureText(failure, t) } : item)));
    }
  };

  const submit = (event?: FormEvent) => {
    event?.preventDefault();
    const body = text.trim();
    if (!body) {
      setError(t('support.empty', 'Write a message'));
      return;
    }
    if (reportMode && !category) {
      setError(t('support.chooseCategory', 'Choose what is wrong'));
      return;
    }
    if (reportMode && body.length < REPORT_MESSAGE_MIN) {
      setError(t('support.reportTooShort', 'Describe the problem in at least 10 characters'));
      return;
    }
    if (LONG_NUMBER_PATTERN.test(body)) {
      setError(t('support.hasNumber', 'Remove identification, card or document numbers: they are not needed to answer you'));
      return;
    }
    setError(undefined);
    const request: SendSupportMessage = {
      body,
      clientMessageId: newClientMessageId(),
      serviceId,
      organizationId,
      reportCategory: reportMode && category ? category : undefined,
      language: locale,
      website,
    };
    setPending((current) => [...current, { clientMessageId: request.clientMessageId, body, contextLabel, state: 'sending', request }]);
    setText('');
    if (reportMode) setReport(false);
    void deliver(request);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  };

  const data = thread.data;
  const empty = data && data.messages.length === 0 && pending.length === 0;
  const reportEntity = serviceId ? 'FUNCTION' : 'ORGANIZATION';

  return (
    <div className="shell py-10 sm:py-14 lg:py-16">
      <SectionHeading
        eyebrow={t('nav.support', 'Support')}
        title={t('support.title', 'Support')}
        description={t('support.subtitle', 'Write to us about any service or about this site. Your messages and our replies stay together in one private conversation. You do not need an account.')}
      />

      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1.7fr)_minmax(18rem,0.9fr)] lg:items-start">
        <Card className="overflow-hidden">
          {thread.isPending ? (
            <CardBody className="p-6"><SkeletonText lines={5} /></CardBody>
          ) : thread.error ? (
            <ErrorState error={thread.error} onRetry={() => void thread.refetch()} />
          ) : (
            <>
              {empty ? (
                <div className="space-y-3 px-6 py-8 text-sm leading-6 text-secondary">
                  <p className="flex items-center gap-2 text-lg font-semibold text-foreground">
                    <LifeBuoyIcon className="h-5 w-5" aria-hidden="true" />
                    {t('support.emptyTitle', 'How support works')}
                  </p>
                  <ul className="list-disc space-y-1 ps-5">
                    <li>{t('support.how1', 'Messages about a service go to the organization responsible for it; other messages go to the central support team.')}</li>
                    <li>{t('support.how2Live', 'Your message reaches the support inbox at once, and replies appear here as soon as staff send them. No response time is promised.')}</li>
                    <li>{t('support.how3', 'Replies appear here. While you browse this site, the Support link in the menu shows when a reply is waiting.')}</li>
                    <li>{t('support.how4', 'Use “Ask about this service” or “Report a problem” on a service page to attach that service to your message.')}</li>
                  </ul>
                </div>
              ) : (
                <>
                <div className="flex justify-end border-b border-line px-4 py-2 sm:px-6">
                  <LiveStatus channel="visitor" pollSeconds={THREAD_POLL_MS / 1000} />
                </div>
                <SupportThreadView
                  messages={data?.messages ?? []}
                  pending={pending}
                  organizationName={organizationLabel}
                  onRetry={(id) => {
                    const item = pending.find((entry) => entry.clientMessageId === id);
                    if (item) void deliver(item.request);
                  }}
                />
                </>
              )}

              <form onSubmit={submit} noValidate className="relative space-y-3 border-t border-line bg-background px-4 py-4 sm:px-6">
                <div className="flex flex-wrap items-end gap-3">
                  <div className="min-w-[14rem] flex-1">
                    <Field label={t('support.subject', 'About')}>
                      {(control) => (
                        <Select
                          {...control}
                          size="sm"
                          value={serviceId ? String(serviceId) : organizationId ? `org-${organizationId}` : ''}
                          onValueChange={(value) => setContext(value.startsWith('org-') ? '' : value)}
                          options={[
                            { value: '', label: t('support.generalContext', 'General support') },
                            ...(organizationId ? [{ value: `org-${organizationId}`, label: organizationLabel(organizationId) }] : []),
                            ...services.map(({ item, name }) => ({ value: String(item.id), label: name })),
                            ...(serviceId && !service && functions.isSuccess ? [{ value: String(serviceId), label: `#${serviceId}` }] : []),
                          ]}
                        />
                      )}
                    </Field>
                  </div>
                  {(serviceId || organizationId) && (
                    <label className="flex min-h-10 items-center gap-2 text-sm text-foreground">
                      <Checkbox checked={reportMode} onChange={(event) => setReport(event.target.checked)} />
                      {t('support.reportToggle', 'This is a report of incorrect information')}
                    </label>
                  )}
                </div>
                {reportMode && (
                  <Field label={t('report.category', 'What is wrong?')} required>
                    {(control) => (
                      <Select
                        {...control}
                        size="sm"
                        value={category}
                        placeholder={t('fnAdmin.choose', 'Select…')}
                        onValueChange={(value) => setCategory(value as ReportCategory)}
                        options={REPORT_CATEGORIES[reportEntity].map((value) => ({ value, label: reportCategoryLabel(value, t) }))}
                      />
                    )}
                  </Field>
                )}
                {service && !reportMode && empty && <ServiceAnswerPreview service={service.item} organization={organization} />}
                <SensitiveDataNotice />
                <div>
                  <label htmlFor={messageId} className="sr-only">{t('support.message', 'Message')}</label>
                  <div className="flex items-end gap-2">
                    <Textarea
                      id={messageId}
                      rows={2}
                      maxLength={SUPPORT_MESSAGE_MAX}
                      value={text}
                      aria-invalid={error ? true : undefined}
                      aria-describedby={`${messageId}-hint`}
                      placeholder={reportMode ? t('support.reportPlaceholder', 'What is wrong, and where is the correct information published?') : t('support.placeholder', 'Write a message…')}
                      onChange={(event) => setText(event.target.value)}
                      onKeyDown={onKeyDown}
                      className="min-h-[3.25rem] flex-1 resize-y"
                    />
                    <Button type="submit" variant="dark" size="icon" aria-label={t('support.send', 'Send')} disabled={send.isPending && pending.some((item) => item.state === 'sending')}>
                      <SendIcon aria-hidden="true" />
                    </Button>
                  </div>
                  <p id={`${messageId}-hint`} className="mt-1 flex flex-wrap justify-between gap-2 text-xs text-secondary">
                    <span>{t('support.keyboardHint', 'Enter sends, Shift+Enter starts a new line.')}</span>
                    <span className="tabular-nums">{text.length} / {SUPPORT_MESSAGE_MAX}</span>
                  </p>
                  {error && <p className="mt-1 text-sm text-status-danger" role="alert">{error}</p>}
                </div>
                {contextLabel && (
                  <p className="flex items-center gap-2 text-xs text-secondary">
                    {t('support.goesTo', 'Your message goes to')}: <span className="font-medium text-foreground">{organizationLabel(service?.item.organizationId ?? organizationId ?? null)}</span>
                    <button type="button" className="inline-flex items-center gap-1 underline-offset-4 fine:hover:underline" onClick={() => setContext('')}>
                      <XIcon className="h-3 w-3" aria-hidden="true" />{t('support.clearContext', 'Remove subject')}
                    </button>
                  </p>
                )}
                <div aria-hidden="true" className="pointer-events-none absolute -start-[10000px] top-0 h-px w-px overflow-hidden">
                  <label htmlFor={`${messageId}-website`}>{t('report.honeypot', 'Leave this field empty')}</label>
                  <input id={`${messageId}-website`} name="website" type="text" tabIndex={-1} autoComplete="off" value={website} onChange={(event) => setWebsite(event.target.value)} />
                </div>
              </form>
            </>
          )}
        </Card>

        <aside aria-label={t('support.settings', 'Support settings')}>
          {data && <SupportSettings key={data.identity} thread={data} />}
        </aside>
      </div>
    </div>
  );
}
