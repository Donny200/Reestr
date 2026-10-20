import { useMemo, useState } from 'react';
import { UserCheckIcon, UserMinusIcon } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { useAssignFunction, useAssigneeCandidates, useUnassignFunction } from '../../features/functions/queries';
import { InlineAlert } from '../../features/functions/InlineAlert';
import type { AdminFunction } from '../../types/adminFunctions';
import { errorMessage } from '../../utils/errors';
import { formatDate } from '../../utils/format';
import { Button } from '../ui/Button';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { Select } from '../ui/Select';

export const ASSIGN_PERMISSION = 'FUNCTIONS_ASSIGN';

/** Responsible staff member of the card. Only active members of the card's organization are offered. */
export function FunctionAssignee({ record, busy }: { record: AdminFunction; busy: boolean }) {
  const { t, locale } = useI18n();
  const { user, hasPermission } = useAuth();
  const target = record.revisionOfId ?? record.id;
  const canAssign = hasPermission(ASSIGN_PERMISSION) && record.organizationId !== null && !record.revisionOfId;
  const candidates = useAssigneeCandidates(target, canAssign);
  const assign = useAssignFunction(target);
  const unassign = useUnassignFunction(target);
  const [selected, setSelected] = useState('');
  const assignee = record.assignee ?? null;
  const options = useMemo(
    () => (candidates.data ?? []).map((member) => ({ value: String(member.id), label: `${[member.firstName, member.lastName].filter(Boolean).join(' ') || member.email} · ${member.email}` })),
    [candidates.data],
  );
  const stale = assignee && candidates.data && !candidates.data.some((member) => member.id === assignee.userId);

  const run = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action();
      setSelected('');
      toast.success(message);
    } catch (error) {
      toast.error(errorMessage(error, t));
    }
  };

  return (
    <Card id="function-assignee">
      <CardHeader title={t('assignee.title', 'Responsible staff member')} />
      <CardBody className="space-y-4">
        {assignee ? (
          <dl className="text-sm">
            <dt className="text-secondary">{t('assignee.current', 'Keeps this card current')}</dt>
            <dd className="mt-1 font-medium text-foreground wrap-anywhere">
              {assignee.name ?? assignee.email ?? `#${assignee.userId}`}
              {assignee.userId === user?.id && <span className="ms-1 text-secondary">({t('verification.byYou', 'you')})</span>}
              {assignee.email && assignee.name && <span className="block text-xs font-normal text-secondary">{assignee.email}</span>}
              {assignee.assignedAt && <span className="block text-xs font-normal tabular-nums text-secondary">{t('assignee.since', 'Since')} {formatDate(assignee.assignedAt, locale)}</span>}
            </dd>
          </dl>
        ) : (
          <p className="text-sm leading-6 text-secondary">{t('assignee.none', 'Nobody is assigned. Reminders stay visible to the whole organization.')}</p>
        )}
        {record.revisionOfId && <p className="text-xs leading-5 text-secondary">{t('assignee.onPublished', 'Responsibility is managed on the published card.')}</p>}
        {stale && (
          <InlineAlert tone="warning">{t('assignee.stale', 'This person is no longer an active member of the organization. Choose someone else.')}</InlineAlert>
        )}
        {canAssign && (
          <>
            <Select
              value={selected}
              onValueChange={setSelected}
              aria-label={t('assignee.choose', 'Choose a staff member')}
              placeholder={t('assignee.choose', 'Choose a staff member')}
              options={options}
              disabled={busy || candidates.isPending}
              size="sm"
            />
            {candidates.error && <InlineAlert tone="danger">{errorMessage(candidates.error, t)}</InlineAlert>}
            <div className="grid gap-2">
              <Button variant="outline" icon={<UserCheckIcon />} disabled={busy || !selected || Number(selected) === assignee?.userId} loading={assign.isPending}
                onClick={() => void run(() => assign.mutateAsync(Number(selected)), t('assignee.assigned', 'Responsible staff member updated'))}>
                {assignee ? t('assignee.reassign', 'Reassign') : t('assignee.assign', 'Assign')}
              </Button>
              {assignee && (
                <Button variant="ghost" icon={<UserMinusIcon />} disabled={busy} loading={unassign.isPending}
                  onClick={() => void run(() => unassign.mutateAsync(undefined), t('assignee.removed', 'Assignment removed'))}>
                  {t('assignee.remove', 'Remove assignment')}
                </Button>
              )}
            </div>
          </>
        )}
        <p className="text-xs leading-5 text-secondary">{t('assignee.hint', 'Verification reminders and visitor reports about this card are routed to this person; the organization still sees everything.')}</p>
      </CardBody>
    </Card>
  );
}
