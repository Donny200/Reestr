import { useId, useRef, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { CalendarCheckIcon, InfoIcon } from 'lucide-react';
import { BackLink } from '../components/catalog/BackLink';
import { OrganizationContactDetails } from '../components/catalog/OrganizationContactDetails';
import { SectionHeading } from '../components/home/SectionHeading';
import { Button } from '../components/ui/Button';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import { Field } from '../components/ui/Field';
import { Input } from '../components/ui/Input';
import { SkeletonText } from '../components/ui/Skeleton';
import { ErrorState } from '../components/ui/States';
import { useI18n } from '../contexts/i18n';
import { managePath, newAccessToken, newRequestId } from '../features/appointments/access';
import { laterFrom, zonedDate } from '../features/appointments/format';
import { useAvailability, useBookAppointment } from '../features/appointments/queries';
import { SlotPicker } from '../features/appointments/SlotPicker';
import { usePublicFunction } from '../features/functions/queries';
import { InlineAlert } from '../features/functions/InlineAlert';
import { usePublicOrganizations } from '../features/organizations/queries';
import type { AppointmentSlot } from '../types/appointments';
import { hasContactDetails } from '../utils/contact';
import { errorMessage, fieldErrorsOf, statusOf } from '../utils/errors';
import { localizedText } from '../utils/translations';

const PHONE = /^\+?[0-9][0-9 ()-]{5,22}[0-9]$/;
const EMAIL = /^[^@\s]{1,64}@[^@\s]+\.[^@\s]{2,}$/;

/**
 * Booking without an account in the calendar the organization manages on this site. The server re-checks the slot
 * and refuses it when it was taken in the meantime; the visitor sees the booking as confirmed only after that.
 */
export function BookAppointment() {
  const { id } = useParams<{ id: string }>();
  const serviceId = Number(id);
  const { t, locale } = useI18n();
  const navigate = useNavigate();
  const service = usePublicFunction(serviceId);
  const organizations = usePublicOrganizations();
  const [pages, setPages] = useState<(string | undefined)[]>([undefined]);
  const from = pages[pages.length - 1];
  const availability = useAvailability(serviceId, from);
  const book = useBookAppointment();
  const [date, setDate] = useState<string | null>(null);
  const [slot, setSlot] = useState<AppointmentSlot | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [website, setWebsite] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  // One token and request id per chosen slot: retrying after a network error cannot create a second booking.
  const attempt = useRef<{ startsAt: string; token: string; requestId: string } | null>(null);
  const honeypotId = useId();

  const organization = organizations.data?.find((item) => item.id === service.data?.organizationId);
  const serviceName = service.data ? localizedText(service.data.name, service.data.nameTranslations, locale) : '';
  const organizationName = organization ? localizedText(organization.name, organization.nameTranslations, locale) : null;

  const chooseSlot = (value: AppointmentSlot) => {
    setSlot(value);
    setFailure(null);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!slot) next.slot = t('appointments.chooseSlotFirst', 'Choose a day and a time');
    if (name.trim().length < 2) next.name = t('appointments.nameRequired', 'Enter your name');
    if (!phone.trim() && !email.trim()) next.phone = t('appointments.contactRequired', 'Enter a phone number or an e-mail address so the organization can reach you');
    if (phone.trim() && !PHONE.test(phone.trim())) next.phone = t('appointments.phoneInvalid', 'Enter a phone number with digits only, e.g. +998 90 123 45 67');
    if (email.trim() && !EMAIL.test(email.trim())) next.email = t('support.emailInvalid', 'Enter a valid e-mail address or leave the field empty');
    setErrors(next);
    if (Object.keys(next).length > 0 || !slot) return;
    if (!attempt.current || attempt.current.startsAt !== slot.startsAt) {
      attempt.current = { startsAt: slot.startsAt, token: newAccessToken(), requestId: newRequestId() };
    }
    const { token, requestId } = attempt.current;
    setFailure(null);
    try {
      const booked = await book.mutateAsync({ serviceId, startsAt: slot.startsAt, name: name.trim(), phone: phone.trim(), email: email.trim(),
        accessToken: token, clientRequestId: requestId, language: locale, website });
      attempt.current = null;
      navigate(managePath(booked.reference, token), { state: { justBooked: true } });
    } catch (error) {
      if (statusOf(error) === 409) {
        attempt.current = null;
        setSlot(null);
        setFailure(errorMessage(error, t) || t('appointments.slotTaken', 'This time was just booked by someone else. Choose another time.'));
        void availability.refetch();
        return;
      }
      const fields = fieldErrorsOf(error, t);
      if (Object.keys(fields).length > 0) setErrors(fields);
      setFailure(statusOf(error) === 429
        ? t('appointments.rateLimited', 'Too many booking attempts in a short time. Please wait a few minutes and try again.')
        : `${errorMessage(error, t)} ${t('appointments.retryHint', 'Your time is not booked yet. You can try again.')}`);
    }
  };

  const loading = service.isPending || availability.isPending;
  const loadError = service.error ?? (availability.error && statusOf(availability.error) !== 404 ? availability.error : null);

  return (
    <div className="shell py-8 sm:py-12 lg:py-16">
      <BackLink to={`/functions/${serviceId}`} label={t('appointments.backToService', 'Back to the service')} />
      <div className="mt-6">
        <SectionHeading
          eyebrow={t('appointments.eyebrow', 'Appointment')}
          title={t('appointments.bookTitle', 'Book an appointment')}
          description={serviceName ? `${serviceName}${organizationName ? ` · ${organizationName}` : ''}` : undefined}
        />
      </div>

      {loading ? (
        <Card className="mt-8"><CardBody><SkeletonText lines={6} /></CardBody></Card>
      ) : loadError ? (
        <Card className="mt-8"><ErrorState error={loadError} onRetry={() => { void service.refetch(); void availability.refetch(); }} /></Card>
      ) : !availability.data?.bookable ? (
        <Card className="mt-8">
          <CardBody className="space-y-4">
            <p className="flex items-start gap-2.5 text-base leading-7 text-foreground">
              <InfoIcon className="mt-1 h-4 w-4 shrink-0" aria-hidden="true" />
              {t('appointments.notBookable', 'This organization does not take online appointments on this site. Contact the organization directly to arrange a visit.')}
            </p>
            {organization && hasContactDetails(organization.contact) && <OrganizationContactDetails contact={organization.contact} />}
          </CardBody>
        </Card>
      ) : (
        <form onSubmit={(event) => void submit(event)} noValidate className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1.6fr)_minmax(18rem,0.9fr)] lg:items-start">
          <Card>
            <CardHeader title={t('appointments.whenTitle', '1. When do you want to come?')} />
            <CardBody className="space-y-4">
              <p className="text-sm leading-6 text-secondary">
                {t('appointments.calendarNote', 'You book a time in the organization’s own appointment calendar on this site. Only times that are still free are shown.')}
              </p>
              {availability.data.visitorNote && <InlineAlert tone="info">{availability.data.visitorNote}</InlineAlert>}
              <SlotPicker
                availability={availability.data}
                loading={availability.isFetching && !availability.data}
                error={null}
                onRetry={() => void availability.refetch()}
                date={date}
                onDate={(value) => { setDate(value); setSlot(null); }}
                slot={slot}
                onSlot={chooseSlot}
                onLater={laterFrom(availability.data) ? () => { setPages((current) => [...current, laterFrom(availability.data)]); setDate(null); setSlot(null); } : undefined}
                onEarlier={pages.length > 1 ? () => { setPages((current) => current.slice(0, -1)); setDate(null); setSlot(null); } : undefined}
              />
              {errors.slot && <p className="text-sm text-status-danger" role="alert">{errors.slot}</p>}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title={t('appointments.whoTitle', '2. Your contact details')} />
            <CardBody className="space-y-4">
              <p className="text-sm leading-6 text-secondary">
                {t('appointments.privacy', 'Only the organization’s staff see these details, to reach you about this appointment. They are deleted 30 days after the appointment. You do not need an account.')}
              </p>
              <Field label={t('appointments.name', 'Your name')} required error={errors.name}>
                {(control) => <Input {...control} autoComplete="name" maxLength={100} value={name} onChange={(event) => setName(event.target.value)} />}
              </Field>
              <Field label={t('appointments.phone', 'Phone')} hint={t('appointments.contactHint', 'A phone number or an e-mail address is needed.')} error={errors.phone}>
                {(control) => <Input {...control} type="tel" autoComplete="tel" inputMode="tel" maxLength={30} value={phone} onChange={(event) => setPhone(event.target.value)} />}
              </Field>
              <Field label={t('support.email', 'E-mail')} error={errors.email}>
                {(control) => <Input {...control} type="email" autoComplete="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} />}
              </Field>
              <div aria-hidden="true" className="pointer-events-none absolute -start-[10000px] top-0 h-px w-px overflow-hidden">
                <label htmlFor={honeypotId}>{t('report.honeypot', 'Leave this field empty')}</label>
                <input id={honeypotId} name="website" type="text" tabIndex={-1} autoComplete="off" value={website} onChange={(event) => setWebsite(event.target.value)} />
              </div>
              {slot && (
                <p className="rounded-control bg-surface px-3 py-2 text-sm text-foreground" role="status">
                  {t('appointments.selected', 'Selected')}: <span className="font-semibold">{zonedDate(slot.startsAt, availability.data.timeZone ?? 'Asia/Tashkent', locale)}, {slot.start}</span>
                </p>
              )}
              {failure && <InlineAlert tone="danger">{failure}</InlineAlert>}
              <Button type="submit" variant="dark" className="w-full" icon={<CalendarCheckIcon />} loading={book.isPending}>
                {t('appointments.confirm', 'Book this time')}
              </Button>
              <p className="text-xs leading-5 text-secondary">
                {t('appointments.notApplication', 'An appointment reserves a time for your visit. It is not an application and does not confirm that you are eligible for the service.')}
              </p>
            </CardBody>
          </Card>
        </form>
      )}
    </div>
  );
}
