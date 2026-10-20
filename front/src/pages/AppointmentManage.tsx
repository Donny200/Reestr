import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { CalendarClockIcon, CheckCircle2Icon, CopyIcon, LinkIcon, PrinterIcon, XCircleIcon } from 'lucide-react';
import { toast } from 'sonner';
import { SectionHeading } from '../components/home/SectionHeading';
import { VisitSheet } from '../components/catalog/VisitSheet';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { buttonVariants } from '../components/ui/buttonVariants';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { ConfirmModal } from '../components/ui/Modal';
import { SkeletonText } from '../components/ui/Skeleton';
import { EmptyState, ErrorState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { tokenFromHash } from '../features/appointments/access';
import { appointmentStatusText, laterFrom, zonedDate } from '../features/appointments/format';
import { usePublicSite, useRescheduleAvailability, useVisitorAppointment, useVisitorAppointmentActions } from '../features/appointments/queries';
import { SlotPicker } from '../features/appointments/SlotPicker';
import { InlineAlert } from '../features/functions/InlineAlert';
import { usePublicFunction } from '../features/functions/queries';
import { usePublicOrganizations } from '../features/organizations/queries';
import type { AppointmentSlot } from '../types/appointments';
import { errorMessage, statusOf } from '../utils/errors';
import { copyText } from '../utils/share';
import { localizedText } from '../utils/translations';

/**
 * A visitor's appointment, opened with the private manage link (reference + secret token in the URL fragment). Shows
 * the status, time, place and service, and lets the visitor print a visit sheet, move or cancel the booking.
 */
export function AppointmentManage() {
  const { reference = '' } = useParams<{ reference: string }>();
  const location = useLocation();
  const { t, locale } = useI18n();
  const token = tokenFromHash(location.hash);
  const justBooked = Boolean((location.state as { justBooked?: boolean } | null)?.justBooked);
  const appointment = useVisitorAppointment(reference.toUpperCase(), token);
  const actions = useVisitorAppointmentActions(reference.toUpperCase(), token);
  const service = usePublicFunction(appointment.data?.serviceId ?? Number.NaN);
  const organizations = usePublicOrganizations();
  const publicSite = usePublicSite();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [moving, setMoving] = useState(false);
  const [pages, setPages] = useState<(string | undefined)[]>([undefined]);
  const options = useRescheduleAvailability(reference.toUpperCase(), token, pages[pages.length - 1], moving);
  const [date, setDate] = useState<string | null>(null);
  const [slot, setSlot] = useState<AppointmentSlot | null>(null);
  const [moveError, setMoveError] = useState<string | null>(null);

  if (!token) {
    return (
      <div className="shell py-10 sm:py-14">
        <Card>
          <EmptyState
            icon={<LinkIcon className="h-6 w-6" aria-hidden="true" />}
            title={t('appointments.linkNeeded', 'Open your personal appointment link')}
            description={t('appointments.linkNeededText', 'For your privacy an appointment can only be opened with the link shown when it was booked. Ask the organization if you lost it.')}
          />
        </Card>
      </div>
    );
  }

  const data = appointment.data;
  const organization = organizations.data?.find((item) => item.id === data?.organizationId);
  const organizationName = organization ? localizedText(organization.name, organization.nameTranslations, locale)
    : data?.organizationName ? localizedText(data.organizationName, data.organizationNameTranslations, locale) : null;
  const serviceName = data ? localizedText(data.serviceName, data.serviceNameTranslations, locale) : '';
  const contact = organization?.contact ?? data?.organizationContact ?? null;

  const copyLink = async () => {
    const ok = await copyText(window.location.href);
    if (ok) toast.success(t('appointments.linkCopied', 'Link copied'));
  };

  const cancel = async () => {
    try {
      await actions.cancel.mutateAsync();
      setConfirmCancel(false);
      toast.success(t('appointments.cancelled', 'Your appointment is cancelled'));
    } catch (error) {
      toast.error(errorMessage(error, t));
    }
  };

  const move = async () => {
    if (!slot) return;
    setMoveError(null);
    try {
      await actions.reschedule.mutateAsync(slot.startsAt);
      setMoving(false);
      setSlot(null);
      setDate(null);
      toast.success(t('appointments.moved', 'Your appointment was moved'));
    } catch (error) {
      setSlot(null);
      setMoveError(statusOf(error) === 409
        ? t('appointments.slotTaken', 'This time was just booked by someone else. Choose another time.')
        : errorMessage(error, t));
    }
  };

  return (
    <div className="shell py-8 sm:py-12 lg:py-16 print:py-0">
      {data && service.data && <VisitSheet record={service.data} organization={organization} appointment={data} publicBaseUrl={publicSite.data?.publicBaseUrl} />}
      <div className={service.data ? 'print:hidden' : undefined}>
        <SectionHeading
          eyebrow={t('appointments.eyebrow', 'Appointment')}
          title={t('appointments.manageTitle', 'Your appointment')}
          description={serviceName ? `${serviceName}${organizationName ? ` · ${organizationName}` : ''}` : undefined}
        />

        {appointment.isPending ? (
          <Card className="mt-8"><CardBody><SkeletonText lines={6} /></CardBody></Card>
        ) : appointment.error ? (
          statusOf(appointment.error) === 404 ? (
            <Card className="mt-8">
              <EmptyState icon={<XCircleIcon className="h-6 w-6" aria-hidden="true" />}
                title={t('appointments.notFound', 'This appointment link is not valid')}
                description={t('appointments.notFoundText', 'Check that you opened the complete link. Ask the organization if you need help.')} />
            </Card>
          ) : (
            <Card className="mt-8"><ErrorState error={appointment.error} onRetry={() => void appointment.refetch()} /></Card>
          )
        ) : data ? (
          <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1.5fr)_minmax(18rem,1fr)] lg:items-start">
            <div className="space-y-6">
              {justBooked && data.status === 'BOOKED' && (
                <Card className="border-status-published">
                  <CardBody className="space-y-3">
                    <p className="flex items-center gap-2 text-lg font-semibold text-foreground" role="status">
                      <CheckCircle2Icon className="h-5 w-5 text-status-published" aria-hidden="true" />
                      {t('appointments.bookedTitle', 'Your appointment is booked')}
                    </p>
                    <p className="text-sm leading-6 text-secondary">
                      {t('appointments.saveLink', 'Save this page’s link (bookmark it or copy it). You need it to see, move or cancel the appointment, and anyone who has it can do so. This site does not keep it in your browser.')}
                    </p>
                    <Button variant="outline" size="sm" icon={<CopyIcon />} onClick={() => void copyLink()}>{t('appointments.copyLink', 'Copy my appointment link')}</Button>
                  </CardBody>
                </Card>
              )}
              <Card>
                <CardHeader title={t('appointments.detailsTitle', 'Appointment details')}
                  actions={<Badge tone={data.status === 'BOOKED' ? 'accent' : undefined}>{appointmentStatusText(data, t)}</Badge>} />
                <CardBody>
                  <dl className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <dt className="micro text-secondary">{t('appointments.reference', 'Booking reference')}</dt>
                      <dd className="mt-1 font-mono text-xl font-semibold tracking-wider text-foreground">{data.reference}</dd>
                    </div>
                    <div>
                      <dt className="micro text-secondary">{t('appointments.statusLabel', 'Status')}</dt>
                      <dd className="mt-1 text-base font-medium text-foreground">{appointmentStatusText(data, t)}</dd>
                    </div>
                    <div>
                      <dt className="micro text-secondary">{t('appointments.date', 'Date')}</dt>
                      <dd className="mt-1 text-base text-foreground">{zonedDate(data.startsAt, data.timeZone, locale)}</dd>
                    </div>
                    <div>
                      <dt className="micro text-secondary">{t('appointments.time', 'Time')}</dt>
                      <dd className="mt-1 text-base tabular-nums text-foreground">{data.localStart}–{data.localEnd} <span className="text-sm text-secondary">({data.timeZone})</span></dd>
                    </div>
                    <div className="sm:col-span-2">
                      <dt className="micro text-secondary">{t('appointments.service', 'Service')}</dt>
                      <dd className="mt-1 text-base text-foreground">
                        {service.data ? <Link to={`/functions/${data.serviceId}`} className="underline-offset-4 fine:hover:underline">{serviceName}</Link> : serviceName}
                      </dd>
                    </div>
                    <div className="sm:col-span-2">
                      <dt className="micro text-secondary">{t('field.organization', 'Organization')}</dt>
                      <dd className="mt-1 text-base text-foreground">{organizationName ?? t('fn.organizationUnknown', 'Organization unknown')}</dd>
                      {contact?.address && <dd className="mt-1 whitespace-pre-line text-sm text-secondary">{contact.address}</dd>}
                      {contact?.phone && <dd className="mt-1 text-sm tabular-nums text-secondary">{contact.phone}</dd>}
                    </div>
                    {(data.visitorName || data.visitorPhone || data.visitorEmail) && (
                      <div className="sm:col-span-2">
                        <dt className="micro text-secondary">{t('appointments.yourDetails', 'Your details')}</dt>
                        <dd className="mt-1 text-sm text-foreground">{[data.visitorName, data.visitorPhone, data.visitorEmail].filter(Boolean).join(' · ')}</dd>
                      </div>
                    )}
                  </dl>
                  {data.visitorNote && <InlineAlert tone="info" className="mt-5">{data.visitorNote}</InlineAlert>}
                  <p className="mt-5 text-sm leading-6 text-secondary">
                    {t('appointments.notApplication', 'An appointment reserves a time for your visit. It is not an application and does not confirm that you are eligible for the service.')}
                  </p>
                </CardBody>
              </Card>

              {moving && (
                <Card>
                  <CardHeader title={t('appointments.moveTitle', 'Choose a new time')} />
                  <CardBody className="space-y-4">
                    <SlotPicker
                      availability={options.data}
                      loading={options.isPending}
                      error={options.error}
                      onRetry={() => void options.refetch()}
                      date={date}
                      onDate={(value) => { setDate(value); setSlot(null); }}
                      slot={slot}
                      onSlot={(value) => { setSlot(value); setMoveError(null); }}
                      onLater={laterFrom(options.data) ? () => { setPages((current) => [...current, laterFrom(options.data)]); setDate(null); setSlot(null); } : undefined}
                      onEarlier={pages.length > 1 ? () => { setPages((current) => current.slice(0, -1)); setDate(null); setSlot(null); } : undefined}
                    />
                    {moveError && <InlineAlert tone="danger">{moveError}</InlineAlert>}
                    <div className="flex flex-wrap gap-2">
                      <Button variant="dark" disabled={!slot} loading={actions.reschedule.isPending} onClick={() => void move()}>
                        {slot ? `${t('appointments.moveTo', 'Move to')} ${slot.start}` : t('appointments.moveTo', 'Move to')}
                      </Button>
                      <Button variant="ghost" onClick={() => { setMoving(false); setSlot(null); setMoveError(null); }}>{t('action.cancel', 'Cancel')}</Button>
                    </div>
                  </CardBody>
                </Card>
              )}
            </div>

            <aside className="space-y-3">
              <Button variant="outline" className="w-full" icon={<PrinterIcon />} onClick={() => window.print()}>{t('visitSheet.print', 'Print visit sheet')}</Button>
              {data.canReschedule && !moving && (
                <Button variant="outline" className="w-full" icon={<CalendarClockIcon />} onClick={() => setMoving(true)}>{t('appointments.move', 'Move to another time')}</Button>
              )}
              {data.canCancel && (
                <Button variant="ghost" className="w-full" icon={<XCircleIcon />} onClick={() => setConfirmCancel(true)}>{t('appointments.cancel', 'Cancel appointment')}</Button>
              )}
              {service.data && <Link to={`/functions/${data.serviceId}`} className={buttonVariants({ variant: 'ghost', size: 'sm' })}>{t('appointments.serviceDetails', 'What to prepare')}</Link>}
            </aside>
          </div>
        ) : null}
      </div>

      <ConfirmModal
        open={confirmCancel}
        title={t('appointments.cancel', 'Cancel appointment')}
        message={t('appointments.cancelConfirm', 'The time becomes free for other visitors. You can book again later if a time is free.')}
        confirmLabel={t('appointments.cancel', 'Cancel appointment')}
        cancelLabel={t('appointments.keep', 'Keep appointment')}
        loading={actions.cancel.isPending}
        onConfirm={() => void cancel()}
        onClose={() => setConfirmCancel(false)}
      />
    </div>
  );
}
