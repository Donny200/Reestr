import type { StatusTone } from '../../components/ui/Badge';
import type { HelpConversationStatus, HelpStatusFilter } from '../../types/help';
import type { Translate } from '../../utils/errors';

export const SUPPORT_MESSAGE_MAX = 2000;
export const REPORT_MESSAGE_MIN = 10;
export const HELP_EMAIL_PATTERN = /^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$/;
/** Same rule as the server: 14+ digits in a row (optionally spaced) look like an identity, card or document number. */
export const LONG_NUMBER_PATTERN = /\d(?:[ -]?\d){13,}/;

export const HELP_VIEW_PERMISSION = 'VISITOR_QUESTIONS_VIEW';
export const HELP_REPLY_PERMISSION = 'VISITOR_QUESTIONS_REPLY';

export const helpStatusLabels: Record<HelpConversationStatus, string> = {
  NEW: 'Open',
  IN_PROGRESS: 'In progress',
  ANSWERED: 'Answered',
  CLOSED: 'Closed',
};

export const helpStatusTones: Record<HelpConversationStatus, StatusTone> = {
  NEW: 'pending',
  IN_PROGRESS: 'pending',
  ANSWERED: 'published',
  CLOSED: 'draft',
};

export const HELP_STATUS_FILTERS: HelpStatusFilter[] = ['ACTIVE', 'OPEN', 'NEW', 'IN_PROGRESS', 'ANSWERED', 'CLOSED', 'ALL'];

export function helpStatusLabel(status: HelpConversationStatus, t: Translate): string {
  return t(`support.status.${status}`, helpStatusLabels[status]);
}

export function helpStatusFilterLabel(filter: HelpStatusFilter, t: Translate): string {
  if (filter === 'ACTIVE') return t('support.filter.ACTIVE', 'Not closed');
  if (filter === 'OPEN') return t('support.filter.OPEN', 'Waiting for a reply');
  if (filter === 'ALL') return t('support.filter.ALL', 'All');
  return helpStatusLabel(filter, t);
}
