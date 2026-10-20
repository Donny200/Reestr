import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  acknowledgeReminder,
  getEngagement,
  getFeedback,
  getOrganizationSummary,
  getQualityQueue,
  getReminders,
} from '../../services/analyticsService';
import type { AnalyticsFilters, QualityQueueFilters } from '../../types/analytics';
import { analyticsKeys } from '../queryKeys';

export const useOrganizationSummary = (filters: AnalyticsFilters, enabled = true) =>
  useQuery({
    queryKey: analyticsKeys.summary(filters),
    queryFn: () => getOrganizationSummary(filters),
    placeholderData: keepPreviousData,
    enabled,
  });

export const useEngagementReport = (filters: AnalyticsFilters, enabled = true) =>
  useQuery({
    queryKey: analyticsKeys.engagement(filters),
    queryFn: () => getEngagement(filters),
    placeholderData: keepPreviousData,
    enabled,
  });

export const useQualityQueue = (filters: QualityQueueFilters) =>
  useQuery({ queryKey: analyticsKeys.quality(filters), queryFn: () => getQualityQueue(filters), placeholderData: keepPreviousData });

export const useReminders = (filters: Pick<AnalyticsFilters, 'organizationId' | 'categoryId'> & { assignee?: 'me' }, enabled = true) =>
  useQuery({ queryKey: analyticsKeys.reminders(filters), queryFn: () => getReminders(filters), enabled });

export const useFeedbackReport = (filters: AnalyticsFilters, enabled = true) =>
  useQuery({ queryKey: analyticsKeys.feedback(filters), queryFn: () => getFeedback(filters), placeholderData: keepPreviousData, enabled });

export function useAcknowledgeReminder() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => acknowledgeReminder(id),
    onSuccess: () => client.invalidateQueries({ queryKey: analyticsKeys.all }),
  });
}
