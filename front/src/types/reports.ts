export type ReportEntityType = 'FUNCTION' | 'ORGANIZATION';

export type ReportStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED';

export type ReportStatusFilter = 'OPEN' | 'ALL' | ReportStatus;

export type ReportCategory =
  | 'INCORRECT_INFORMATION'
  | 'OUTDATED_INFORMATION'
  | 'DOCUMENTS_OR_STEPS'
  | 'FEES_OR_TIMING'
  | 'CONTACT_DETAILS'
  | 'BROKEN_LINK'
  | 'TRANSLATION'
  | 'OTHER';

export interface InformationReport {
  id: number;
  entityType: ReportEntityType;
  entityId: number;
  entityLabel: string;
  organizationId: number;
  category: ReportCategory;
  description: string;
  contact: string | null;
  contactAvailable: boolean;
  language: string | null;
  status: ReportStatus;
  resolutionNote: string | null;
  handledByUserId: number | null;
  createdAt: string;
  updatedAt: string;
  serviceChangedSinceReport: boolean | null;
}

export interface ReportHistoryEntry {
  id: number;
  performedByUserId: number | null;
  performedBy: string;
  action: 'REPORT_RECEIVED' | 'REPORT_STATUS_CHANGE' | string;
  performedAt: string;
  details: string | null;
}

export interface ReportQuery {
  status: ReportStatusFilter;
  category?: ReportCategory;
  entityType?: ReportEntityType;
  entityId?: number;
  serviceCategoryId?: number;
  from?: string;
  to?: string;
  organizationId?: number;
  /** Only "me": reports about cards the caller is responsible for. */
  assignee?: 'me';
}
