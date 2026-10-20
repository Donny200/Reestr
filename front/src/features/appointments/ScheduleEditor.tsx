import { useState, type FormEvent } from 'react';
import { PlusIcon, Trash2Icon } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '../../components/ui/Button';
import { Card, CardBody, CardHeader } from '../../components/ui/Card';
import { Field } from '../../components/ui/Field';
import { Checkbox, Input, Textarea } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { useI18n } from '../../contexts/i18n';
import type { ClosureKind, ClosureRequest, Schedule, Weekday, WorkingDay } from '../../types/appointments';
import { WEEKDAYS } from '../../types/appointments';
import { errorMessage, fieldErrorsOf } from '../../utils/errors';
import { InlineAlert } from '../functions/InlineAlert';
import { calendarDate, closureKindLabels, weekdayLabels } from './format';
import { useScheduleMutations } from './queries';

const ZONES = ['Asia/Tashkent', 'Asia/Samarkand'];
const SLOT_LENGTHS = [10, 15, 20, 30, 45, 60, 90, 120];
const NOTICE = [0, 30, 60, 120, 240, 1440, 2880];
const CLOSURE_KINDS: ClosureKind[] = ['HOLIDAY', 'CLOSED', 'UNAVAILABLE'];


interface DayRow { open: boolean; opens: string; closes: string; breakStarts: string; breakEnds: string }

function rowsOf(schedule: Schedule): Record<Weekday, DayRow> {
  return Object.fromEntries(WEEKDAYS.map((day) => {
    const found = schedule.weeklyHours.find((item) => item.day === day);
    return [day, found
      ? { open: true, opens: found.opens, closes: found.closes, breakStarts: found.breakStarts ?? '', breakEnds: found.breakEnds ?? '' }
      : { open: false, opens: '09:00', closes: '18:00', breakStarts: '13:00', breakEnds: '14:00' }];
  })) as Record<Weekday, DayRow>;
}

/** Availability settings of one organization: working days and hours with a break, slot length, places, window, closures. */
export function ScheduleEditor({ schedule, canManage }: { schedule: Schedule; canManage: boolean }) {
  const { t, locale } = useI18n();
  const mutations = useScheduleMutations(schedule.organizationId);
  const [enabled, setEnabled] = useState(schedule.enabled);
  const [timeZone, setTimeZone] = useState(schedule.timeZone);
  const [slotMinutes, setSlotMinutes] = useState(String(schedule.slotMinutes));
  const [capacity, setCapacity] = useState(String(schedule.capacity));
  const [horizon, setHorizon] = useState(String(schedule.bookingHorizonDays));
  const [notice, setNotice] = useState(String(schedule.minNoticeMinutes));
  const [note, setNote] = useState(schedule.visitorNote ?? '');
  const [days, setDays] = useState(() => rowsOf(schedule));
  const [error, setError] = useState<string | null>(null);
  const [closure, setClosure] = useState<ClosureRequest>({ kind: 'HOLIDAY', startDate: '', endDate: '', startTime: '', endTime: '', note: '' });
  const [closureError, setClosureError] = useState<string | null>(null);

  const setDay = (day: Weekday, patch: Partial<DayRow>) => setDays((current) => ({ ...current, [day]: { ...current[day], ...patch } }));

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    const weeklyHours: WorkingDay[] = WEEKDAYS.filter((day) => days[day].open).map((day) => ({
      day, opens: days[day].opens, closes: days[day].closes, breakStarts: days[day].breakStarts || null, breakEnds: days[day].breakEnds || null,
    }));
    try {
      await mutations.save.mutateAsync({ enabled, timeZone, slotMinutes: Number(slotMinutes), capacity: Number(capacity),
        bookingHorizonDays: Number(horizon), minNoticeMinutes: Number(notice), visitorNote: note, weeklyHours });
      toast.success(t('appointments.scheduleSaved', 'Availability saved'));
    } catch (failure) {
      const fields = fieldErrorsOf(failure);
      setError(Object.values(fields)[0] ?? errorMessage(failure, t));
    }
  };

  const addClosure = async (event: FormEvent) => {
    event.preventDefault();
    setClosureError(null);
    if (!closure.startDate) {
      setClosureError(t('appointments.closureDateRequired', 'Choose the first day'));
      return;
    }
    try {
      await mutations.addClosure.mutateAsync({ ...closure, endDate: closure.endDate || closure.startDate });
      setClosure({ kind: closure.kind, startDate: '', endDate: '', startTime: '', endTime: '', note: '' });
      toast.success(t('appointments.closureAdded', 'Closure added'));
    } catch (failure) {
      const fields = fieldErrorsOf(failure);
      setClosureError(Object.values(fields)[0] ?? errorMessage(failure, t));
    }
  };

  const zones = ZONES.includes(timeZone) ? ZONES : [timeZone, ...ZONES];

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title={t('appointments.availabilityTitle', 'Availability')}
          description={t('appointments.availabilityHint', 'Visitors can book only inside these hours. Changes never move or cancel existing bookings.')} />
        <CardBody>
          <form onSubmit={(event) => void save(event)} noValidate className="space-y-6">
            <fieldset disabled={!canManage} className="space-y-6">
              <label className="flex items-start gap-3 text-base text-foreground">
                <Checkbox className="mt-1" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
                <span>
                  <span className="font-semibold">{t('appointments.enabled', 'Take online appointments')}</span>
                  <span className="block text-sm text-secondary">{t('appointments.enabledHint', 'When on, every published service of this organization shows “Book an appointment”.')}</span>
                </span>
              </label>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
                <Field label={t('appointments.timeZone', 'Time zone')}>
                  {(control) => <Select {...control} size="sm" value={timeZone} onValueChange={setTimeZone} options={zones.map((zone) => ({ value: zone, label: zone }))} />}
                </Field>
                <Field label={t('appointments.slotLength', 'Appointment length')}>
                  {(control) => <Select {...control} size="sm" value={slotMinutes} onValueChange={setSlotMinutes}
                    options={SLOT_LENGTHS.map((value) => ({ value: String(value), label: `${value} ${t('appointments.minutes', 'min')}` }))} />}
                </Field>
                <Field label={t('appointments.capacity', 'Visitors at the same time')}>
                  {(control) => <Input {...control} type="number" min={1} max={50} value={capacity} onChange={(event) => setCapacity(event.target.value)} />}
                </Field>
                <Field label={t('appointments.horizon', 'Days visitors can book ahead')}>
                  {(control) => <Input {...control} type="number" min={1} max={365} value={horizon} onChange={(event) => setHorizon(event.target.value)} />}
                </Field>
                <Field label={t('appointments.notice', 'Minimum notice')}>
                  {(control) => <Select {...control} size="sm" value={notice} onValueChange={setNotice}
                    options={NOTICE.map((value) => ({ value: String(value), label: value === 0 ? t('appointments.noNotice', 'None')
                      : value < 60 ? `${value} ${t('appointments.minutes', 'min')}` : value < 1440 ? `${value / 60} ${t('appointments.hours', 'h')}`
                        : `${value / 1440} ${t('appointments.daysShort', 'd')}` }))} />}
                </Field>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full min-w-[40rem] text-sm">
                  <caption className="mb-2 text-start text-base font-semibold text-foreground">{t('appointments.weeklyHours', 'Working days and hours')}</caption>
                  <thead className="text-start text-xs text-secondary">
                    <tr>
                      <th className="py-2 text-start font-medium">{t('appointments.day', 'Day')}</th>
                      <th className="py-2 text-start font-medium">{t('appointments.opens', 'Opens')}</th>
                      <th className="py-2 text-start font-medium">{t('appointments.closes', 'Closes')}</th>
                      <th className="py-2 text-start font-medium">{t('appointments.breakStarts', 'Break from')}</th>
                      <th className="py-2 text-start font-medium">{t('appointments.breakEnds', 'Break until')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {WEEKDAYS.map((day) => {
                      const row = days[day];
                      const label = t(`appointments.weekday.${day}`, weekdayLabels[day]);
                      return (
                        <tr key={day} className="border-t border-line">
                          <td className="py-2 pe-3">
                            <label className="flex items-center gap-2 font-medium text-foreground">
                              <Checkbox checked={row.open} onChange={(event) => setDay(day, { open: event.target.checked })} />
                              {label}
                            </label>
                          </td>
                          {(['opens', 'closes', 'breakStarts', 'breakEnds'] as const).map((key) => (
                            <td key={key} className="py-2 pe-3">
                              <Input type="time" step={300} aria-label={`${label}: ${t(`appointments.${key}`, key)}`} disabled={!row.open} value={row[key]}
                                onChange={(event) => setDay(day, { [key]: event.target.value })} className="min-h-10 py-1" />
                            </td>
                          ))}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                <p className="mt-2 text-xs text-secondary">{t('appointments.breakHint', 'Leave both break times empty when there is no break.')}</p>
              </div>

              <Field label={t('appointments.visitorNoteLabel', 'Note for visitors (optional)')} hint={t('appointments.visitorNoteHint', 'Shown before booking, e.g. “Please arrive 10 minutes early”.')}>
                {(control) => <Textarea {...control} rows={2} maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} />}
              </Field>
            </fieldset>
            {error && <InlineAlert tone="danger">{error}</InlineAlert>}
            {canManage && <Button type="submit" variant="dark" loading={mutations.save.isPending}>{t('action.save', 'Save')}</Button>}
          </form>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title={t('appointments.closuresTitle', 'Holidays, closed dates and unavailable periods')} />
        <CardBody className="space-y-5">
          {schedule.closures.length === 0 ? (
            <p className="text-sm text-secondary">{t('appointments.noClosures', 'No closures planned.')}</p>
          ) : (
            <ul className="divide-y divide-line">
              {schedule.closures.map((item) => (
                <li key={item.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <span className="text-sm text-foreground">
                    <span className="font-semibold">{t(`appointments.closure.${item.kind}`, closureKindLabels[item.kind])}</span>
                    {' · '}{calendarDate(item.startDate, locale, { day: 'numeric', month: 'short', year: 'numeric' })}
                    {item.endDate !== item.startDate && ` – ${calendarDate(item.endDate, locale, { day: 'numeric', month: 'short', year: 'numeric' })}`}
                    {item.startTime && ` · ${item.startTime}–${item.endTime}`}
                    {item.note && <span className="text-secondary"> · {item.note}</span>}
                  </span>
                  {canManage && (
                    <Button variant="ghost" size="sm" icon={<Trash2Icon />} loading={mutations.removeClosure.isPending && mutations.removeClosure.variables === item.id}
                      onClick={() => void mutations.removeClosure.mutateAsync(item.id).catch((failure) => toast.error(errorMessage(failure, t)))}>
                      {t('appointments.removeClosure', 'Remove')}
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {canManage && (
            <form onSubmit={(event) => void addClosure(event)} noValidate className="grid gap-3 border-t border-line pt-4 sm:grid-cols-3 lg:grid-cols-6 lg:items-end">
              <Field label={t('appointments.closureKind', 'Type')}>
                {(control) => <Select {...control} size="sm" value={closure.kind} onValueChange={(kind) => setClosure((current) => ({ ...current, kind: kind as ClosureKind }))}
                  options={CLOSURE_KINDS.map((kind) => ({ value: kind, label: t(`appointments.closure.${kind}`, closureKindLabels[kind]) }))} />}
              </Field>
              <Field label={t('appointments.firstDay', 'First day')}>
                {(control) => <Input {...control} type="date" value={closure.startDate} onChange={(event) => setClosure((current) => ({ ...current, startDate: event.target.value }))} />}
              </Field>
              <Field label={t('appointments.lastDay', 'Last day')}>
                {(control) => <Input {...control} type="date" value={closure.endDate} onChange={(event) => setClosure((current) => ({ ...current, endDate: event.target.value }))} />}
              </Field>
              <Field label={t('appointments.fromTime', 'From (optional)')}>
                {(control) => <Input {...control} type="time" value={closure.startTime} onChange={(event) => setClosure((current) => ({ ...current, startTime: event.target.value }))} />}
              </Field>
              <Field label={t('appointments.untilTime', 'Until (optional)')}>
                {(control) => <Input {...control} type="time" value={closure.endTime} onChange={(event) => setClosure((current) => ({ ...current, endTime: event.target.value }))} />}
              </Field>
              <Field label={t('appointments.closureNote', 'Note (staff only)')}>
                {(control) => <Input {...control} maxLength={200} value={closure.note} onChange={(event) => setClosure((current) => ({ ...current, note: event.target.value }))} />}
              </Field>
              <div className="sm:col-span-3 lg:col-span-6">
                <p className="mb-2 text-xs text-secondary">{t('appointments.closureHint', 'Without times the whole days are closed. Existing bookings in that period stay; cancel them in Bookings if needed.')}</p>
                {closureError && <InlineAlert tone="danger" className="mb-2">{closureError}</InlineAlert>}
                <Button type="submit" variant="outline" size="sm" icon={<PlusIcon />} loading={mutations.addClosure.isPending}>{t('appointments.addClosure', 'Add closure')}</Button>
              </div>
            </form>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
