import type { ReportCategory } from './reports';

export type HelpConversationStatus = 'NEW' | 'IN_PROGRESS' | 'ANSWERED' | 'CLOSED';

export type HelpStatusFilter = 'ACTIVE' | 'OPEN' | 'ALL' | HelpConversationStatus;

export type HelpAuthor = 'VISITOR' | 'STAFF';

export type SupportIdentity = 'NONE' | 'BROWSER' | 'ACCOUNT';

/** A message of the visitor's own support thread; staff are shown as their organization, never by name. */
export interface SupportMessage {
  id: number;
  author: HelpAuthor;
  body: string;
  createdAt: string;
  clientMessageId: string | null;
  serviceId: number | null;
  contextLabel: string | null;
  /** Organization the message was routed to; null for central support. */
  organizationId: number | null;
  category: ReportCategory | null;
  /** For own messages: when support viewed it. For staff replies: when the visitor read it. */
  readAt: string | null;
}

export interface SupportContact {
  email: string | null;
  notifyByEmail: boolean;
}

export interface SupportThread {
  identity: SupportIdentity;
  messages: SupportMessage[];
  unread: number;
  contact: SupportContact | null;
  notificationsAvailable: boolean;
  recoveryCodeSet: boolean;
}

export interface SupportUnread {
  unread: number;
  latestReplyId: number | null;
}

export interface SendSupportMessage {
  body: string;
  clientMessageId: string;
  serviceId?: number;
  organizationId?: number;
  reportCategory?: ReportCategory;
  language: string;
  website: string;
}

export interface HelpConversationSummary {
  id: number;
  status: HelpConversationStatus;
  serviceId: number | null;
  serviceName: string | null;
  organizationId: number | null;
  general: boolean;
  language: string | null;
  assignedUserId: number | null;
  assignedUserName: string | null;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  lastMessageAuthor: HelpAuthor | null;
  messageCount: number;
  unreadCount: number;
  contactAvailable: boolean;
}

export interface HelpStaffMessage {
  id: number;
  author: HelpAuthor;
  body: string;
  createdAt: string;
  staffUserId: number | null;
  staffLabel: string | null;
  serviceId: number | null;
  contextLabel: string | null;
  category: ReportCategory | null;
  reportId: number | null;
  readAt: string | null;
}

export interface HelpConversationDetail {
  conversation: HelpConversationSummary;
  messages: HelpStaffMessage[];
  /** Only for staff allowed to reply, and only if the visitor left one. */
  contact: SupportContact | null;
}

export interface HelpInboxQuery {
  status?: HelpStatusFilter;
  organizationId?: number;
  general?: boolean;
  unread?: boolean;
  sort?: 'LAST_MESSAGE' | 'UNREAD_FIRST';
  assignee?: 'me';
}

export interface HelpInboxUnread {
  conversations: number;
  messages: number;
}
