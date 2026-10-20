import type { OrganizationContact, TranslatedText } from './api';

export type AppointmentStatus = 'BOOKED' | 'CANCELLED' | 'ATTENDED' | 'NO_SHOW';
export type AppointmentParty = 'VISITOR' | 'STAFF';
export type ClosureKind = 'HOLIDAY' | 'CLOSED' | 'UNAVAILABLE';
export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export const WEEKDAYS: Weekday[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** Local times of the organization ("09:30"); `startsAt` is the instant to book. */
export interface AppointmentSlot {
  start: string;
  end: string;
  startsAt: string;
}

export interface AppointmentDay {
  date: string;
  open: boolean;
  slots: AppointmentSlot[];
}

export interface Availability {
  bookable: boolean;
  serviceId: number;
  organizationId: number;
  timeZone: string | null;
  slotMinutes: number | null;
  visitorNote: string | null;
  lastBookableDate: string | null;
  days: AppointmentDay[];
}

export interface BookAppointmentRequest {
  serviceId: number;
  startsAt: string;
  name: string;
  phone: string;
  email: string;
  accessToken: string;
  clientRequestId: string;
  language: string;
  website: string;
}

/** The organization contact as returned with an appointment (subset of the public profile). */
export type AppointmentOrganizationContact = Pick<OrganizationContact, 'address' | 'phone' | 'workingHours' | 'mapUrl'>;

export interface VisitorAppointment {
  reference: string;
  status: AppointmentStatus;
  cancelledBy: AppointmentParty | null;
  serviceId: number;
  serviceName: string;
  serviceNameTranslations: Record<string, TranslatedText> | null;
  organizationId: number;
  organizationName: string | null;
  organizationNameTranslations: Record<string, TranslatedText> | null;
  organizationContact: AppointmentOrganizationContact | null;
  startsAt: string;
  endsAt: string;
  timeZone: string;
  localDate: string;
  localStart: string;
  localEnd: string;
  visitorName: string | null;
  visitorPhone: string | null;
  visitorEmail: string | null;
  canCancel: boolean;
  canReschedule: boolean;
  visitorNote: string | null;
  createdAt: string;
}

export interface WorkingDay {
  day: Weekday;
  opens: string;
  closes: string;
  breakStarts: string | null;
  breakEnds: string | null;
}

export interface Closure {
  id: number;
  kind: ClosureKind;
  startDate: string;
  endDate: string;
  startTime: string | null;
  endTime: string | null;
  note: string | null;
  createdAt: string;
}

export interface Schedule {
  organizationId: number;
  configured: boolean;
  enabled: boolean;
  timeZone: string;
  slotMinutes: number;
  capacity: number;
  bookingHorizonDays: number;
  minNoticeMinutes: number;
  visitorNote: string | null;
  weeklyHours: WorkingDay[];
  closures: Closure[];
  updatedAt: string | null;
}

export interface ScheduleRequest {
  enabled: boolean;
  timeZone: string;
  slotMinutes: number;
  capacity: number;
  bookingHorizonDays: number;
  minNoticeMinutes: number;
  visitorNote: string;
  weeklyHours: WorkingDay[];
}

export interface ClosureRequest {
  kind: ClosureKind;
  startDate: string;
  endDate: string;
  startTime: string;
  endTime: string;
  note: string;
}

export interface StaffAppointment {
  id: number;
  reference: string;
  status: AppointmentStatus;
  cancelledBy: AppointmentParty | null;
  serviceId: number;
  serviceName: string;
  organizationId: number;
  startsAt: string;
  endsAt: string;
  timeZone: string;
  localDate: string;
  localStart: string;
  localEnd: string;
  seat: number;
  visitorName: string | null;
  visitorPhone?: string;
  visitorEmail?: string;
  contactRemoved: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StaffAppointmentQuery {
  organizationId?: number;
  from?: string;
  to?: string;
  status?: AppointmentStatus;
}
