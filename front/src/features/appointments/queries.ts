import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addClosure,
  bookAppointment,
  cancelAppointment,
  getAppointment,
  getAvailability,
  getPublicSite,
  getRescheduleAvailability,
  getSchedule,
  getStaffAppointments,
  removeClosure,
  rescheduleAppointment,
  saveSchedule,
  setStaffAppointmentStatus,
} from '../../services/appointmentService';
import type { AppointmentStatus, BookAppointmentRequest, ClosureRequest, ScheduleRequest, StaffAppointmentQuery } from '../../types/appointments';
import { appointmentKeys, siteKeys } from '../queryKeys';

/** Free slots change as others book: short stale time, refreshed when the visitor comes back to the tab. */
export const useAvailability = (serviceId: number, from: string | undefined, enabled = true) =>
  useQuery({ queryKey: appointmentKeys.availability(serviceId, from), queryFn: () => getAvailability(serviceId, from),
    enabled: enabled && Number.isFinite(serviceId), staleTime: 15_000, retry: false });

export const useBookAppointment = () => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: BookAppointmentRequest) => bookAppointment(body),
    onSettled: () => client.invalidateQueries({ queryKey: appointmentKeys.availabilities() }),
  });
};

export const useVisitorAppointment = (reference: string, token: string | null) =>
  useQuery({ queryKey: appointmentKeys.visitor(reference), queryFn: () => getAppointment(reference, token ?? ''), enabled: Boolean(token), retry: false });

export const useRescheduleAvailability = (reference: string, token: string | null, from: string | undefined, enabled: boolean) =>
  useQuery({ queryKey: appointmentKeys.reschedule(reference, from), queryFn: () => getRescheduleAvailability(reference, token ?? '', from),
    enabled: enabled && Boolean(token), staleTime: 15_000, retry: false });

export function useVisitorAppointmentActions(reference: string, token: string | null) {
  const client = useQueryClient();
  const refresh = (appointment: unknown) => {
    client.setQueryData(appointmentKeys.visitor(reference), appointment);
    return client.invalidateQueries({ queryKey: appointmentKeys.reschedules(reference) });
  };
  return {
    cancel: useMutation({ mutationFn: () => cancelAppointment(reference, token ?? ''), onSuccess: refresh }),
    reschedule: useMutation({ mutationFn: (startsAt: string) => rescheduleAppointment(reference, token ?? '', startsAt), onSuccess: refresh,
      onError: () => client.invalidateQueries({ queryKey: appointmentKeys.reschedules(reference) }) }),
  };
}

export const useStaffAppointments = (query: StaffAppointmentQuery, enabled = true) =>
  useQuery({ queryKey: appointmentKeys.staff(query), queryFn: () => getStaffAppointments(query), enabled, refetchInterval: 60_000,
    refetchIntervalInBackground: false });

export const useStaffAppointmentStatus = () => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: AppointmentStatus }) => setStaffAppointmentStatus(id, status),
    onSuccess: () => client.invalidateQueries({ queryKey: appointmentKeys.staffLists() }),
  });
};

export const useSchedule = (organizationId: number | null) =>
  useQuery({ queryKey: appointmentKeys.schedule(organizationId ?? 0), queryFn: () => getSchedule(organizationId ?? 0), enabled: organizationId !== null });

export function useScheduleMutations(organizationId: number) {
  const client = useQueryClient();
  const store = (schedule: unknown) => client.setQueryData(appointmentKeys.schedule(organizationId), schedule);
  return {
    save: useMutation({ mutationFn: (body: ScheduleRequest) => saveSchedule(organizationId, body), onSuccess: store }),
    addClosure: useMutation({ mutationFn: (body: ClosureRequest) => addClosure(organizationId, body), onSuccess: store }),
    removeClosure: useMutation({ mutationFn: (closureId: number) => removeClosure(organizationId, closureId), onSuccess: store }),
  };
}

/** Canonical public address for printed pages; null when not configured (never the page's own origin). */
export const usePublicSite = () =>
  useQuery({ queryKey: siteKeys.publicSite(), queryFn: getPublicSite, staleTime: Infinity, retry: false });
