import { apiDownload, apiRequest } from './http';
import type { CsvDelimiter } from '../types/analytics';
import type { InformationReport, ReportHistoryEntry, ReportQuery, ReportStatus } from '../types/reports';

export const getReports = (query: ReportQuery) => apiRequest<InformationReport[]>('/api/reports', { query: { ...query } });

export const getReport = (id: number) => apiRequest<InformationReport>(`/api/reports/${id}`);

export const getReportHistory = (id: number) => apiRequest<ReportHistoryEntry[]>(`/api/reports/${id}/history`);

export const updateReportStatus = (id: number, status: ReportStatus, note: string) =>
  apiRequest<InformationReport>(`/api/reports/${id}/status`, { method: 'PUT', body: { status, note } });

export const exportReports = (query: ReportQuery, delimiter: CsvDelimiter) =>
  apiDownload('/api/reports/export', { ...query, delimiter });

/** Excel or PDF list of visitor reports with the same filters; descriptions and contacts are not included. */
export const exportReportsDocument = (query: ReportQuery, format: 'XLSX' | 'PDF', lang: string) =>
  apiDownload('/api/reports/export', { ...query, format, lang });
