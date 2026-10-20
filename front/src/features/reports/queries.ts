import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getReport, getReportHistory, getReports, updateReportStatus } from '../../services/reportService';
import type { ReportQuery, ReportStatus } from '../../types/reports';
import { analyticsKeys, reportKeys } from '../queryKeys';

export const useReports = (query: ReportQuery, enabled = true) =>
  useQuery({ queryKey: reportKeys.list(query), queryFn: () => getReports(query), enabled });

export const useReport = (id: number | null) =>
  useQuery({ queryKey: reportKeys.detail(id ?? 0), queryFn: () => getReport(id ?? 0), enabled: id !== null });

export const useReportHistory = (id: number | null) =>
  useQuery({ queryKey: reportKeys.history(id ?? 0), queryFn: () => getReportHistory(id ?? 0), enabled: id !== null });


export function useUpdateReportStatus() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, note }: { id: number; status: ReportStatus; note: string }) => updateReportStatus(id, status, note),
    onSuccess: (saved) => {
      client.setQueryData(reportKeys.detail(saved.id), saved);
      return Promise.all([
        client.invalidateQueries({ queryKey: reportKeys.all }),
        client.invalidateQueries({ queryKey: analyticsKeys.all }),
      ]);
    },
  });
}
