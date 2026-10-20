import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BellIcon, CheckIcon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader } from '../../components/ui/Card';
import { SkeletonText } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/States';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { errorMessage } from '../../utils/errors';
import { formatDate } from '../../utils/format';
import { localizedText } from '../../utils/translations';
import { useAcknowledgeReminder, useReminders } from './queries';
import { editorPath, issueLabel } from './qualityIssues';

const PREVIEW = 10;

interface RemindersSectionProps {
  organizationId?: number;
  categoryId?: number;
}

export function RemindersSection({ organizationId, categoryId }: RemindersSectionProps) {
  const { t, locale } = useI18n();
  const { hasPermission, user } = useAuth();
  const [mine, setMine] = useState(false);
  const reminders = useReminders({ organizationId, categoryId, assignee: mine ? 'me' : undefined });
  const acknowledge = useAcknowledgeReminder();
  const [expanded, setExpanded] = useState(false);
  const items = reminders.data?.items ?? [];
  const total = reminders.data?.total ?? 0;
  const visible = expanded ? items : items.slice(0, PREVIEW);
  const canOpenEditor = hasPermission('FUNCTIONS_VIEW');

  if (!mine && !reminders.isPending && !reminders.error && total === 0) return null;

  return (
    <section id="reminders" aria-label={t('reminders.title', 'Reminders')} className="scroll-mt-24">
      <Card>
        <CardHeader
          icon={<BellIcon />}
          title={`${t('reminders.title', 'Reminders')}${total > 0 ? ` (${total})` : ''}`}
          description={t('reminders.subtitle', 'Created by a daily check. An acknowledged reminder returns after 14 days if the issue is still open.')}
          actions={
            <label className="flex cursor-pointer items-center gap-2 text-sm text-foreground">
              <input type="checkbox" className="h-4 w-4 accent-current" checked={mine} onChange={(event) => setMine(event.target.checked)} />
              {t('reminders.onlyMine', 'Only cards assigned to me')}
            </label>
          }
        />
        {reminders.isPending ? (
          <div className="px-5 py-5"><SkeletonText lines={3} /></div>
        ) : reminders.error ? (
          <ErrorState error={reminders.error} onRetry={() => void reminders.refetch()} />
        ) : (
          <ul className="divide-y divide-line">
            {visible.map((reminder) => {
              const name = localizedText(reminder.functionName, reminder.functionNameTranslations, locale) ?? `#${reminder.functionId}`;
              return (
                <li key={reminder.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="font-medium text-foreground wrap-anywhere">{name}</p>
                    <p className="mt-0.5 text-sm text-secondary">
                      {issueLabel(reminder.issue, t)} · <span className="tabular-nums">{t('reminders.detected', 'Detected:')} {formatDate(reminder.detectedAt, locale)}</span>
                      {reminder.assignedUserId != null && (
                        <span> · {t('assignee.short', 'Responsible')}: {reminder.assignedUserId === user?.id ? t('verification.byYou', 'you') : reminder.assignedUserName ?? `#${reminder.assignedUserId}`}</span>
                      )}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {canOpenEditor && (
                      <Link to={editorPath(reminder.functionId, reminder.issue)} className="inline-flex min-h-9 items-center rounded-pill px-3 text-sm font-medium text-accent-text underline-offset-4 fine:hover:underline">
                        {t('quality.fixInEditor', 'Fix in editor')}
                        <span className="sr-only">: {name}</span>
                      </Link>
                    )}
                    <Button
                      variant="outline"
                      size="sm"
                      icon={<CheckIcon />}
                      loading={acknowledge.isPending && acknowledge.variables === reminder.id}
                      disabled={acknowledge.isPending}
                      onClick={() =>
                        acknowledge.mutate(reminder.id, {
                          onSuccess: () => toast.success(t('reminders.acknowledged', 'Reminder hidden for 14 days')),
                          onError: (error) => toast.error(errorMessage(error, t)),
                        })
                      }
                    >
                      {t('reminders.acknowledge', 'Acknowledge')}
                      <span className="sr-only">: {name}</span>
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
        {(items.length > visible.length || total > items.length) && (
          <div className="flex flex-col gap-3 border-t border-line px-5 py-4 sm:flex-row sm:items-center">
            {total > items.length && (
              <p className="text-sm text-secondary">{t('reminders.limited', 'Only the 200 newest reminders are listed. Narrow the filters to see the others.')}</p>
            )}
            {items.length > visible.length && (
              <Button variant="outline" size="sm" className="sm:ms-auto" onClick={() => setExpanded(true)}>
                {t('reminders.showAll', 'Show all reminders')}
              </Button>
            )}
          </div>
        )}
      </Card>
    </section>
  );
}
