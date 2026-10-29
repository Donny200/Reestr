import type { AnalyticsFilters, QualityQueueFilters } from '../types/analytics';
import type { FunctionQuery } from '../types/api';
import type { StaffAppointmentQuery } from '../types/appointments';
import type { HelpInboxQuery } from '../types/help';
import type { ReportQuery } from '../types/reports';

export const organizationKeys = {
  all: ['organizations'] as const,
  list: () => [...organizationKeys.all, 'list'] as const,
  public: () => [...organizationKeys.all, 'public'] as const,
  publicDetail: (id: number) => [...organizationKeys.all, 'public-detail', id] as const,
};

export const staffKeys = {
  all: ['staff'] as const,
  orgAdmins: () => [...staffKeys.all, 'org-admins'] as const,
  orgAdminCandidates: () => [...staffKeys.all, 'org-admin-candidates'] as const,
  moderators: () => [...staffKeys.all, 'moderators'] as const,
  moderatorCandidates: () => [...staffKeys.all, 'moderator-candidates'] as const,
};

export const roleKeys = {
  all: ['roles'] as const,
  list: () => [...roleKeys.all, 'list'] as const,
  permissions: () => [...roleKeys.all, 'permissions'] as const,
  assignmentCandidates: () => [...roleKeys.all, 'assignment-candidates'] as const,
};

export const languageKeys = {
  all: ['languages'] as const,
  list: () => [...languageKeys.all, 'list'] as const,
  search: (query: string) => [...languageKeys.all, 'search', query] as const,
  coverage: () => [...languageKeys.all, 'coverage'] as const,
};

export const legacyUserKeys = {
  all: ['legacy-users'] as const,
  list: () => [...legacyUserKeys.all, 'list'] as const,
};

export const referenceKeys = {
  regions: ['regions'] as const,
};

export const functionKeys = {
  all: ['functions'] as const,
  admin: () => [...functionKeys.all, 'admin'] as const,
  detail: (id: number) => [...functionKeys.all, 'detail', id] as const,
  audit: (id: number) => [...functionKeys.all, 'audit', id] as const,
  review: (id: number) => [...functionKeys.all, 'review', id] as const,
  categories: () => [...functionKeys.all, 'categories'] as const,
  capabilities: () => [...functionKeys.all, 'capabilities'] as const,
  public: () => [...functionKeys.all, 'public'] as const,
  publicList: (query: FunctionQuery) => [...functionKeys.public(), 'list', query] as const,
  publicDetail: (id: number) => [...functionKeys.public(), 'detail', id] as const,
  publicHistory: (id: number) => [...functionKeys.public(), 'history', id] as const,
  options: (global: boolean, userId: number | null) => [...functionKeys.all, 'options', global, userId] as const,
  assigneeCandidates: (id: number) => [...functionKeys.all, 'assignee-candidates', id] as const,
};

export const reportKeys = {
  all: ['reports'] as const,
  list: (query: ReportQuery) => [...reportKeys.all, 'list', query] as const,
  detail: (id: number) => [...reportKeys.all, 'detail', id] as const,
  history: (id: number) => [...reportKeys.all, 'history', id] as const,
};

export const helpKeys = {
  all: ['help'] as const,
  thread: () => [...helpKeys.all, 'thread'] as const,
  unread: () => [...helpKeys.all, 'unread'] as const,
  inboxes: () => [...helpKeys.all, 'inbox'] as const,
  inbox: (query: HelpInboxQuery) => [...helpKeys.inboxes(), query] as const,
  inboxUnread: () => [...helpKeys.all, 'inbox-unread'] as const,
  details: () => [...helpKeys.all, 'detail'] as const,
  detail: (id: number) => [...helpKeys.details(), id] as const,
  candidates: (id: number) => [...helpKeys.all, 'candidates', id] as const,
};

export const appointmentKeys = {
  all: ['appointments'] as const,
  availabilities: () => [...appointmentKeys.all, 'availability'] as const,
  availability: (serviceId: number, from: string | undefined) => [...appointmentKeys.availabilities(), serviceId, from ?? ''] as const,
  visitor: (reference: string) => [...appointmentKeys.all, 'visitor', reference] as const,
  reschedules: (reference: string) => [...appointmentKeys.all, 'reschedule', reference] as const,
  reschedule: (reference: string, from: string | undefined) => [...appointmentKeys.reschedules(reference), from ?? ''] as const,
  staffLists: () => [...appointmentKeys.all, 'staff'] as const,
  staff: (query: StaffAppointmentQuery) => [...appointmentKeys.staffLists(), query] as const,
  schedule: (organizationId: number) => [...appointmentKeys.all, 'schedule', organizationId] as const,
};

export const siteKeys = {
  publicSite: () => ['site', 'public'] as const,
};

export const analyticsKeys = {
  all: ['analytics'] as const,
  summary: (filters: AnalyticsFilters) => [...analyticsKeys.all, 'summary', filters] as const,
  engagement: (filters: AnalyticsFilters) => [...analyticsKeys.all, 'engagement', filters] as const,
  quality: (filters: QualityQueueFilters) => [...analyticsKeys.all, 'quality', filters] as const,
  reminders: (filters: Pick<AnalyticsFilters, 'organizationId' | 'categoryId'> & { assignee?: 'me' }) => [...analyticsKeys.all, 'reminders', filters] as const,
  feedback: (filters: AnalyticsFilters) => [...analyticsKeys.all, 'feedback', filters] as const,
  assignments: (organizationId: number | undefined, state: string | undefined) => [...analyticsKeys.all, 'assignments', organizationId ?? null, state ?? null] as const,
};

export const authKeys = {
  all: ['auth'] as const,
  mfa: () => [...authKeys.all, 'mfa'] as const,
  sessions: () => [...authKeys.all, 'sessions'] as const,
  passwordResetOptions: () => [...authKeys.all, 'password-reset-options'] as const,
  userSessions: (id: number) => [...authKeys.all, 'user-sessions', id] as const,
};
