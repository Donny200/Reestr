import { apiRequest, SESSIONLESS } from './http';
import type {
  Availability,
  BookAppointmentRequest,
  ClosureRequest,
  Schedule,
  ScheduleRequest,
  StaffAppointment,
  StaffAppointmentQuery,
  AppointmentStatus,
  VisitorAppointment,
} from '../types/appointments';

/** Visitors manage a booking with its reference and the secret token their browser chose; the token goes in a header. */
const tokenHeader = (token: string) => ({ 'X-Appointment-Token': token });

export const getAvailability = (serviceId: number, from?: string, days = 14) =>
  apiRequest<Availability>(`/api/appointments/services/${serviceId}/availability`, { query: { from, days }, ...SESSIONLESS });

export const bookAppointment = (body: BookAppointmentRequest) =>
  apiRequest<VisitorAppointment>('/api/appointments', { method: 'POST', body, ...SESSIONLESS });

export const getAppointment = (reference: string, token: string) =>
  apiRequest<VisitorAppointment>(`/api/appointments/${encodeURIComponent(reference)}`, { headers: tokenHeader(token), ...SESSIONLESS });

export const getRescheduleAvailability = (reference: string, token: string, from?: string, days = 14) =>
  apiRequest<Availability>(`/api/appointments/${encodeURIComponent(reference)}/availability`, { query: { from, days }, headers: tokenHeader(token), ...SESSIONLESS });

export const cancelAppointment = (reference: string, token: string) =>
  apiRequest<VisitorAppointment>(`/api/appointments/${encodeURIComponent(reference)}/cancel`, { method: 'POST', headers: tokenHeader(token), ...SESSIONLESS });

export const rescheduleAppointment = (reference: string, token: string, startsAt: string) =>
  apiRequest<VisitorAppointment>(`/api/appointments/${encodeURIComponent(reference)}/reschedule`, {
    method: 'POST', body: { startsAt }, headers: tokenHeader(token), ...SESSIONLESS,
  });

export const getStaffAppointments = (query: StaffAppointmentQuery) =>
  apiRequest<StaffAppointment[]>('/api/appointments/staff', { query: { ...query } });

export const setStaffAppointmentStatus = (id: number, status: AppointmentStatus) =>
  apiRequest<StaffAppointment>(`/api/appointments/staff/${id}/status`, { method: 'PUT', body: { status } });

export const getSchedule = (organizationId: number) => apiRequest<Schedule>(`/api/appointments/schedules/${organizationId}`);

export const saveSchedule = (organizationId: number, body: ScheduleRequest) =>
  apiRequest<Schedule>(`/api/appointments/schedules/${organizationId}`, { method: 'PUT', body });

export const addClosure = (organizationId: number, body: ClosureRequest) =>
  apiRequest<Schedule>(`/api/appointments/schedules/${organizationId}/closures`, { method: 'POST', body });

export const removeClosure = (organizationId: number, closureId: number) =>
  apiRequest<Schedule>(`/api/appointments/schedules/${organizationId}/closures/${closureId}`, { method: 'DELETE' });

export const getPublicSite = () => apiRequest<{ publicBaseUrl: string | null }>('/api/functions/public-site', SESSIONLESS);
