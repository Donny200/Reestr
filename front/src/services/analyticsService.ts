import { apiDownload, apiRequest, SESSIONLESS } from './http';
import type {
  AnalyticsFilters,
  CsvDelimiter,
  EngagementEvent,
  EngagementReport,
  FeedbackReport,
  OrganizationSummary,
  QualityQueue,
  QualityQueueFilters,
  QualityReminderPage,
} from '../types/analytics';

export const recordEngagement = (event: EngagementEvent) =>
  apiRequest<void>('/api/analytics/events', { method: 'POST', body: event, ...SESSIONLESS });

export const getOrganizationSummary = (filters: AnalyticsFilters) =>
  apiRequest<OrganizationSummary>('/api/analytics/summary', { query: { ...filters } });

export const getEngagement = (filters: AnalyticsFilters) =>
  apiRequest<EngagementReport>('/api/analytics/engagement', { query: { ...filters } });

export const getQualityQueue = (filters: QualityQueueFilters) =>
  apiRequest<QualityQueue>('/api/analytics/quality-queue', { query: { ...filters } });

export const getReminders = (filters: Pick<AnalyticsFilters, 'organizationId' | 'categoryId'> & { assignee?: 'me' }) =>
  apiRequest<QualityReminderPage>('/api/analytics/reminders', { query: { ...filters } });

export const getFeedback = (filters: AnalyticsFilters) =>
  apiRequest<FeedbackReport>('/api/analytics/feedback', { query: { ...filters } });

export const acknowledgeReminder = (id: number) =>
  apiRequest<void>(`/api/analytics/reminders/${id}/acknowledge`, { method: 'POST' });

export const exportEngagement = (filters: AnalyticsFilters, breakdown: 'SERVICES' | 'DAILY', delimiter: CsvDelimiter) =>
  apiDownload('/api/analytics/engagement/export', { ...filters, breakdown, delimiter });

/** Organization report with the dashboard filters: Excel workbook (several sheets) or printable PDF. */
export const exportOrganizationReport = (filters: AnalyticsFilters, format: 'XLSX' | 'PDF', lang: string) =>
  apiDownload('/api/analytics/report/export', { ...filters, format, lang });

export const exportQualityQueue = (filters: QualityQueueFilters, delimiter: CsvDelimiter) =>
  apiDownload('/api/analytics/quality-queue/export', { ...filters, delimiter });
