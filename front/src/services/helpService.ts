import { apiRequest, SESSIONLESS } from './http';
import type { StaffMember } from '../types/api';
import type {
  HelpConversationDetail,
  HelpConversationStatus,
  HelpConversationSummary,
  HelpInboxQuery,
  HelpInboxUnread,
  SendSupportMessage,
  SupportContact,
  SupportMessage,
  SupportThread,
  SupportUnread,
} from '../types/help';

/** The visitor is recognised by an HttpOnly cookie or their account; no conversation id or secret is ever sent. */
export const getSupportThread = () => apiRequest<SupportThread>('/api/help/support', SESSIONLESS);

export const getSupportUnread = () => apiRequest<SupportUnread>('/api/help/support/unread', SESSIONLESS);

export const sendSupportMessage = (body: SendSupportMessage) =>
  apiRequest<SupportMessage>('/api/help/support/messages', { method: 'POST', body, ...SESSIONLESS });

export const markSupportRead = (upTo: number) =>
  apiRequest<SupportUnread>('/api/help/support/read', { method: 'POST', body: { upTo }, ...SESSIONLESS });

export const updateSupportContact = (contact: SupportContact) =>
  apiRequest<SupportContact>('/api/help/support/contact', { method: 'PUT', body: contact, ...SESSIONLESS });

export const createRecoveryCode = () => apiRequest<{ code: string }>('/api/help/support/recovery-code', { method: 'POST', ...SESSIONLESS });

export const recoverSupport = (code: string) =>
  apiRequest<SupportThread>('/api/help/support/recover', { method: 'POST', body: { code }, ...SESSIONLESS });

export const forgetSupportBrowser = () => apiRequest<void>('/api/help/support/session', { method: 'DELETE', ...SESSIONLESS });

export const getHelpInbox = (query: HelpInboxQuery) =>
  apiRequest<HelpConversationSummary[]>('/api/help/inbox', { query: { ...query } });

export const getHelpInboxUnread = () => apiRequest<HelpInboxUnread>('/api/help/inbox/unread');

export const getHelpInboxDetail = (id: number) => apiRequest<HelpConversationDetail>(`/api/help/inbox/${id}`);

export const markHelpConversationRead = (id: number, upTo: number) =>
  apiRequest<HelpConversationDetail>(`/api/help/inbox/${id}/read`, { method: 'POST', body: { upTo } });

export const replyToHelpConversation = (id: number, message: string, clientMessageId: string) =>
  apiRequest<HelpConversationDetail>(`/api/help/inbox/${id}/replies`, { method: 'POST', body: { message, clientMessageId } });

export const setHelpConversationStatus = (id: number, status: HelpConversationStatus) =>
  apiRequest<HelpConversationDetail>(`/api/help/inbox/${id}/status`, { method: 'PUT', body: { status } });

export const getHelpAssigneeCandidates = (id: number) => apiRequest<StaffMember[]>(`/api/help/inbox/${id}/candidates`);

export const assignHelpConversation = (id: number, userId: number | null) =>
  apiRequest<HelpConversationDetail>(`/api/help/inbox/${id}/assignee`, { method: 'PUT', body: { userId } });
