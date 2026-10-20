import type { FunctionStatus } from './adminFunctions';
import type { AssigneeSummary, TranslatedText, VerificationStatus } from './api';

export const ENGAGEMENT_EVENT_TYPES = ['CATALOG_VIEW', 'SERVICE_VIEW', 'OFFICIAL_LINK_CLICK', 'PHONE_CLICK', 'MAP_CLICK', 'PRINT'] as const;

export type EngagementEventType = (typeof ENGAGEMENT_EVENT_TYPES)[number];

export type EngagementCounts = Record<EngagementEventType, number>;

export type Granularity = 'DAY' | 'WEEK';

export interface EngagementEvent {
  type: EngagementEventType;
  serviceId?: number;
  organizationId?: number;
}

export interface PeriodInfo {
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  timeZone: string;
}

export interface SeriesPoint {
  start: string;
  end: string;
  counts: EngagementCounts;
}

export interface ServiceEngagement {
  subject: 'SERVICE' | 'ORGANIZATION' | 'CATALOG';
  functionId: number | null;
  name: string | null;
  nameTranslations: Record<string, TranslatedText> | null;
  status: FunctionStatus | null;
  organizationId: number | null;
  categoryId: number | null;
  category: string | null;
  views: number;
  actions: number;
  counts: EngagementCounts;
}

export interface EngagementReport {
  period: PeriodInfo;
  granularity: Granularity;
  totals: EngagementCounts;
  previousTotals: EngagementCounts;
  series: SeriesPoint[];
  topServices: ServiceEngagement[];
}

export const QUALITY_ISSUE_TYPES = ['VERIFICATION_OVERDUE', 'SOURCE_MISSING', 'INFORMATION_INCOMPLETE', 'TRANSLATIONS_MISSING'] as const;

export type QualityIssueType = (typeof QUALITY_ISSUE_TYPES)[number];

export type QualityIssueReason =
  | 'NEVER_VERIFIED'
  | 'RECHECK_DUE'
  | 'CHANGED_SINCE_VERIFICATION'
  | 'UNUSABLE'
  | 'REQUIRED_FIELDS'
  | 'LANGUAGES';

export interface QualityIssue {
  type: QualityIssueType;
  reason: QualityIssueReason;
  details: string[];
}

export interface QualityQueueItem {
  functionId: number;
  name: string;
  nameTranslations: Record<string, TranslatedText> | null;
  status: FunctionStatus;
  organizationId: number | null;
  categoryId: number | null;
  category: string | null;
  verificationStatus: VerificationStatus;
  lastVerifiedAt: string | null;
  verificationDueAt: string | null;
  officialSourceUrl: string | null;
  issues: QualityIssue[];
}

export interface QualityQueue {
  translationsChecked: boolean;
  activeLanguages: string[];
  items: QualityQueueItem[];
}

export interface ReportCounts {
  unresolved: number;
  newReports: number;
  inProgress: number;
  receivedInPeriod: number;
  receivedInPreviousPeriod: number;
}

export interface OrganizationSummary {
  generatedAt: string;
  period: PeriodInfo;
  organizationId: number | null;
  categoryId: number | null;
  services: Record<FunctionStatus, number>;
  servicesNeedingAttention: number;
  attention: Record<QualityIssueType, number>;
  translationsChecked: boolean;
  activeLanguages: string[];
  reports: ReportCounts;
  openReminders: number;
  assignedToMe: { services: number; openReminders: number; openReports: number } | null;
}

export interface QualityReminder {
  id: number;
  functionId: number;
  functionName: string | null;
  functionNameTranslations: Record<string, TranslatedText> | null;
  functionStatus: FunctionStatus | null;
  organizationId: number;
  issue: QualityIssueType;
  detectedAt: string;
  notifiedAt: string;
  assignedUserId?: number | null;
  assignedUserName?: string | null;
}

export const ASSIGNMENT_STATES = ['ASSIGNED', 'UNASSIGNED', 'NEEDS_REASSIGNMENT', 'UNKNOWN'] as const;

export type AssignmentState = (typeof ASSIGNMENT_STATES)[number];

export interface AssignmentItem {
  functionId: number;
  name: string;
  nameTranslations: Record<string, TranslatedText> | null;
  status: FunctionStatus;
  organizationId: number | null;
  categoryId: number | null;
  category: string | null;
  assignee: AssigneeSummary | null;
  state: AssignmentState;
}

export interface AssignmentOverview {
  counts: { assigned: number; unassigned: number; needsReassignment: number };
  membersChecked: boolean;
  items: AssignmentItem[];
}

export interface FeedbackTotals {
  helpful: number;
  notHelpful: number;
  total: number;
}

export interface ServiceFeedback {
  functionId: number;
  name: string | null;
  nameTranslations: Record<string, TranslatedText> | null;
  status: FunctionStatus | null;
  organizationId: number | null;
  categoryId: number | null;
  category: string | null;
  helpful: number;
  notHelpful: number;
  total: number;
}

/** Usefulness of the published text; not a rating of the organization, the service or its staff. */
export interface FeedbackReport {
  period: PeriodInfo;
  totals: FeedbackTotals;
  previousTotals: FeedbackTotals;
  services: ServiceFeedback[];
}

export interface QualityReminderPage {
  total: number;
  items: QualityReminder[];
}

export interface AnalyticsFilters {
  organizationId?: number;
  categoryId?: number;
  from: string;
  to: string;
}

export interface QualityQueueFilters {
  organizationId?: number;
  categoryId?: number;
  issue?: QualityIssueType;
  status?: FunctionStatus;
}

export type CsvDelimiter = 'COMMA' | 'SEMICOLON';
