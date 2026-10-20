import { useId } from 'react';
import { ChevronRightIcon } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { SkeletonText } from '../../components/ui/Skeleton';
import { ErrorState } from '../../components/ui/States';
import { useI18n } from '../../contexts/i18n';
import { cn } from '../../lib/cn';
import type { AppointmentSlot, Availability } from '../../types/appointments';
import { calendarDate } from './format';

interface SlotPickerProps {
  availability: Availability | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  date: string | null;
  onDate: (date: string) => void;
  slot: AppointmentSlot | null;
  onSlot: (slot: AppointmentSlot) => void;
  /** Loads the next days; absent when the booking window ends with the shown days. */
  onLater?: () => void;
  onEarlier?: () => void;
}

/** Days with free times, then the free times of the chosen day, in the organization's time zone. */
export function SlotPicker({ availability, loading, error, onRetry, date, onDate, slot, onSlot, onLater, onEarlier }: SlotPickerProps) {
  const { t, locale } = useI18n();
  const daysId = useId();
  const timesId = useId();
  if (loading) return <SkeletonText lines={4} />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  const days = availability?.days ?? [];
  const chosen = days.find((day) => day.date === date);
  const anyFree = days.some((day) => day.slots.length > 0);

  return (
    <div className="space-y-5">
      <div>
        <h3 id={daysId} className="text-base font-semibold text-foreground">{t('appointments.chooseDay', 'Choose a day')}</h3>
        <ul aria-labelledby={daysId} className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
          {days.map((day) => {
            const free = day.slots.length;
            const selected = day.date === date;
            return (
              <li key={day.date}>
                <button
                  type="button"
                  disabled={free === 0}
                  aria-pressed={selected}
                  onClick={() => onDate(day.date)}
                  className={cn(
                    'flex min-h-16 w-full flex-col items-start justify-center rounded-card-sm border px-3 py-2 text-start transition-colors duration-snap focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
                    selected ? 'border-ink bg-ink text-ink-fg' : 'border-line bg-background text-foreground fine:hover:bg-surface',
                    free === 0 && 'cursor-not-allowed opacity-50 fine:hover:bg-background',
                  )}
                >
                  <span className="text-sm font-semibold">{calendarDate(day.date, locale)}</span>
                  <span className={cn('text-xs tabular-nums', selected ? 'text-ink-secondary' : 'text-secondary')}>
                    {!day.open ? t('appointments.closed', 'Closed') : free === 0 ? t('appointments.full', 'No free times') : `${free} ${t('appointments.freeTimes', 'free times')}`}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
        <div className="mt-3 flex flex-wrap gap-2">
          {onEarlier && <Button variant="ghost" size="sm" onClick={onEarlier}>{t('appointments.earlierDays', 'Earlier days')}</Button>}
          {onLater && <Button variant="outline" size="sm" icon={<ChevronRightIcon />} onClick={onLater}>{t('appointments.laterDays', 'Later days')}</Button>}
        </div>
        {!anyFree && <p className="mt-3 text-sm text-secondary" role="status">{t('appointments.noFreeDays', 'There are no free times in these days. Try later days.')}</p>}
      </div>

      {chosen && (
        <div>
          <h3 id={timesId} className="text-base font-semibold text-foreground">
            {t('appointments.chooseTime', 'Choose a time')} · {calendarDate(chosen.date, locale, { weekday: 'long', day: 'numeric', month: 'long' })}
          </h3>
          <ul aria-labelledby={timesId} className="mt-3 flex flex-wrap gap-2">
            {chosen.slots.map((item) => {
              const selected = slot?.startsAt === item.startsAt;
              return (
                <li key={item.startsAt}>
                  <button
                    type="button"
                    aria-pressed={selected}
                    onClick={() => onSlot(item)}
                    className={cn(
                      'min-h-11 min-w-[5.5rem] rounded-pill border px-4 text-sm font-semibold tabular-nums transition-colors duration-snap focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
                      selected ? 'border-ink bg-ink text-ink-fg' : 'border-line bg-background text-foreground fine:hover:bg-surface',
                    )}
                  >
                    {item.start}
                  </button>
                </li>
              );
            })}
          </ul>
          {availability?.timeZone && (
            <p className="mt-2 text-xs text-secondary">{t('appointments.timeZoneNote', 'Times are local times of the organization')} ({availability.timeZone})</p>
          )}
        </div>
      )}
    </div>
  );
}
