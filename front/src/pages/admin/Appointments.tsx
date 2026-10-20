import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { PageHeader } from '../../components/layout/PageHeader';
import { StatusPill, type StatusTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card, CardBody } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { SkeletonText } from '../../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../../components/ui/States';
import { Tabs } from '../../components/ui/Tabs';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { APPOINTMENT_MANAGE_PERMISSION } from '../../features/appointments/access';
import { appointmentStatusLabels, appointmentStatusText, calendarDate } from '../../features/appointments/format';
import { useSchedule, useStaffAppointmentStatus, useStaffAppointments } from '../../features/appointments/queries';
import { ScheduleEditor } from '../../features/appointments/ScheduleEditor';
import { parseId, useScopeOptions } from '../../features/analytics/scope';
import type { AppointmentStatus, StaffAppointment } from '../../types/appointments';
import { errorMessage } from '../../utils/errors';
import { addDays } from '../../utils/format';
import { usePatchSearchParams } from '../../hooks/usePatchSearchParams';

const STATUSES: AppointmentStatus[] = ['BOOKED', 'CANCELLED', 'ATTENDED', 'NO_SHOW'];
const tones: Record<AppointmentStatus, StatusTone> = { BOOKED: 'published', CANCELLED: 'danger', ATTENDED: 'draft', NO_SHOW: 'pending' };

function today() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/** Bookings and availability of the caller's organizations; the backend limits both to their organizations. */
export function Appointments() {
  const { t, locale } = useI18n();
  const { user, hasPermission } = useAuth();
  const scope = useScopeOptions();
  const [params, set] = usePatchSearchParams();
  const tab = params.get('tab') === 'availability' ? 'availability' : 'bookings';
  const canManage = hasPermission(APPOINTMENT_MANAGE_PERMISSION);
  const organizationId = parseId(params.get('organization'));
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(addDays(today(), 14));
  const [status, setStatus] = useState<AppointmentStatus | ''>('');
  const bookings = useStaffAppointments({ organizationId, from, to, status: status || undefined }, tab === 'bookings');
  const changeStatus = useStaffAppointmentStatus();

  const ownOrganizations = scope.organizations.filter((option) => option.value !== '');
  // The availability editor needs one organization: the chosen one, else the first the user can manage.
  const scheduleOrganization = organizationId ?? (ownOrganizations[0] ? Number(ownOrganizations[0].value) : user?.organizationIds[0] ?? null);
  const schedule = useSchedule(tab === 'availability' ? scheduleOrganization : null);

  const grouped = useMemo(() => {
    const map = new Map<string, StaffAppointment[]>();
    (bookings.data ?? []).forEach((item) => map.set(item.localDate, [...(map.get(item.localDate) ?? []), item]));
    return [...map.entries()];
  }, [bookings.data]);

  const act = async (item: StaffAppointment, target: AppointmentStatus) => {
    try {
      await changeStatus.mutateAsync({ id: item.id, status: target });
      toast.success(`${item.reference}: ${t(`appointments.status.${target}`, appointmentStatusLabels[target])}`);
    } catch (error) {
      toast.error(errorMessage(error, t));
    }
  };

  const now = Date.now();

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.admin')}
        title={t('appointments.adminTitle', 'Appointments')}
        description={t('appointments.adminSubtitle', 'Bookings of your organization and the availability visitors can book. There is no external scheduling system: this calendar is managed here.')}
      />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Tabs label={t('appointments.adminTitle', 'Appointments')} value={tab} onChange={(id) => set({ tab: id === 'bookings' ? '' : id })}
          items={[{ id: 'bookings', label: t('appointments.bookingsTab', 'Bookings') }, { id: 'availability', label: t('appointments.availabilityTab', 'Availability') }]} />
        {(scope.showOrganizations || ownOrganizations.length > 1) && (
          <div className="min-w-[14rem]">
            <Select size="sm" aria-label={t('field.organization')} value={organizationId ? String(organizationId) : ''}
              onValueChange={(value) => set({ organization: value })}
              options={tab === 'availability' ? ownOrganizations : scope.organizations} placeholder={t('fnAdmin.choose', 'Choose')} />
          </div>
        )}
      </div>

      {tab === 'bookings' ? (
        <Card>
          <div className="grid gap-3 border-b border-line px-5 py-4 sm:grid-cols-3">
            <Input type="date" aria-label={t('appointments.from', 'From')} value={from} onChange={(event) => setFrom(event.target.value || today())} />
            <Input type="date" aria-label={t('appointments.to', 'To')} value={to} onChange={(event) => setTo(event.target.value || from)} />
            <Select size="sm" aria-label={t('field.status')} value={status} onValueChange={(value) => setStatus(value as AppointmentStatus | '')}
              options={[{ value: '', label: t('appointments.anyStatus', 'Any status') },
                ...STATUSES.map((value) => ({ value, label: t(`appointments.status.${value}`, appointmentStatusLabels[value]) }))]} />
          </div>
          {bookings.isPending ? (
            <CardBody><SkeletonText lines={5} /></CardBody>
          ) : bookings.error ? (
            <ErrorState error={bookings.error} onRetry={() => void bookings.refetch()} />
          ) : grouped.length === 0 ? (
            <EmptyState title={t('appointments.noBookings', 'No bookings in this period')} />
          ) : (
            <div className="divide-y divide-line">
              {grouped.map(([date, items]) => (
                <section key={date} className="px-5 py-4" aria-label={calendarDate(date, locale, { weekday: 'long', day: 'numeric', month: 'long' })}>
                  <h2 className="text-sm font-semibold text-foreground">{calendarDate(date, locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</h2>
                  <ul className="mt-3 space-y-3">
                    {items.map((item) => {
                      const started = new Date(item.startsAt).getTime() <= now;
                      return (
                        <li key={item.id} className="flex flex-wrap items-start justify-between gap-3 rounded-card-sm border border-line p-3">
                          <div className="min-w-0 space-y-1 text-sm">
                            <p className="flex flex-wrap items-center gap-2">
                              <span className="font-semibold tabular-nums text-foreground">{item.localStart}–{item.localEnd}</span>
                              <StatusPill tone={tones[item.status]}>{appointmentStatusText(item, t)}</StatusPill>
                              <span className="font-mono text-xs text-secondary">{item.reference}</span>
                            </p>
                            <p className="text-foreground">{item.serviceName}{scope.showOrganizations && <span className="text-secondary"> · {scope.organizationName(item.organizationId)}</span>}</p>
                            <p className="text-secondary">
                              {item.contactRemoved ? t('appointments.contactRemoved', 'Visitor details removed after the retention period')
                                : [item.visitorName, item.visitorPhone, item.visitorEmail].filter(Boolean).join(' · ')}
                            </p>
                          </div>
                          {canManage && (
                            <div className="flex flex-wrap gap-2">
                              {(item.status === 'BOOKED' || item.status === 'NO_SHOW') && started && (
                                <Button variant="outline" size="sm" onClick={() => void act(item, 'ATTENDED')}>{t('appointments.markAttended', 'Attended')}</Button>
                              )}
                              {(item.status === 'BOOKED' || item.status === 'ATTENDED') && started && (
                                <Button variant="outline" size="sm" onClick={() => void act(item, 'NO_SHOW')}>{t('appointments.markNoShow', 'Did not come')}</Button>
                              )}
                              {item.status === 'BOOKED' && (
                                <Button variant="ghost" size="sm" onClick={() => void act(item, 'CANCELLED')}>{t('appointments.cancelBooking', 'Cancel booking')}</Button>
                              )}
                            </div>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                </section>
              ))}
            </div>
          )}
        </Card>
      ) : scheduleOrganization === null ? (
        <Card><EmptyState title={t('appointments.chooseOrganization', 'Choose an organization')} /></Card>
      ) : schedule.isPending ? (
        <Card><CardBody><SkeletonText lines={8} /></CardBody></Card>
      ) : schedule.error ? (
        <Card><ErrorState error={schedule.error} onRetry={() => void schedule.refetch()} /></Card>
      ) : schedule.data ? (
        <ScheduleEditor key={`${schedule.data.organizationId}-${schedule.data.updatedAt ?? 'new'}`}
          schedule={schedule.data} canManage={canManage} />
      ) : null}
    </div>
  );
}
