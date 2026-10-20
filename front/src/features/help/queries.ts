import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  assignHelpConversation,
  createRecoveryCode,
  forgetSupportBrowser,
  getHelpAssigneeCandidates,
  getHelpInbox,
  getHelpInboxDetail,
  getHelpInboxUnread,
  getSupportThread,
  getSupportUnread,
  markHelpConversationRead,
  markSupportRead,
  recoverSupport,
  replyToHelpConversation,
  sendSupportMessage,
  setHelpConversationStatus,
  updateSupportContact,
} from '../../services/helpService';
import type { HelpConversationDetail, HelpConversationStatus, HelpInboxQuery, SendSupportMessage, SupportContact, SupportThread } from '../../types/help';
import { helpKeys } from '../queryKeys';
import { BADGE_POLL_MS, INBOX_POLL_MS, THREAD_POLL_MS, useLiveInterval } from './liveSupport';
import { markSupportActive } from './supportActivity';

/**
 * Server-sent events (liveSupport.ts) trigger reloads as soon as something changes; polling is the fallback, at the
 * normal pace while no stream is connected and every minute as a safety net while one is. Hidden tabs never poll.
 */
export const useSupportThread = () =>
  useQuery({ queryKey: helpKeys.thread(), queryFn: getSupportThread, refetchInterval: useLiveInterval('visitor', THREAD_POLL_MS),
    refetchIntervalInBackground: false });

export const useSupportUnread = (enabled: boolean) =>
  useQuery({ queryKey: helpKeys.unread(), queryFn: getSupportUnread, enabled, refetchInterval: useLiveInterval('visitor', BADGE_POLL_MS),
    refetchIntervalInBackground: false });

export function useSendSupportMessage() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: SendSupportMessage) => sendSupportMessage(body),
    onSuccess: (message) => {
      markSupportActive();
      client.setQueryData<SupportThread>(helpKeys.thread(), (current) =>
        current && !current.messages.some((item) => item.id === message.id)
          ? { ...current, identity: current.identity === 'NONE' ? 'BROWSER' : current.identity, messages: [...current.messages, message] }
          : current);
      return client.invalidateQueries({ queryKey: helpKeys.thread() });
    },
  });
}

export function useMarkSupportRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (upTo: number) => markSupportRead(upTo),
    onSuccess: (unread) => {
      client.setQueryData(helpKeys.unread(), unread);
      return client.invalidateQueries({ queryKey: helpKeys.thread() });
    },
  });
}

export function useSupportSettings() {
  const client = useQueryClient();
  const refresh = () => client.invalidateQueries({ queryKey: helpKeys.all });
  return {
    contact: useMutation({ mutationFn: (contact: SupportContact) => updateSupportContact(contact), onSuccess: refresh }),
    recoveryCode: useMutation({ mutationFn: createRecoveryCode, onSuccess: refresh }),
    recover: useMutation({
      mutationFn: (code: string) => recoverSupport(code),
      onSuccess: (thread) => {
        markSupportActive();
        client.setQueryData(helpKeys.thread(), thread);
        return refresh();
      },
    }),
    forget: useMutation({ mutationFn: forgetSupportBrowser, onSuccess: refresh }),
  };
}

export const useHelpInbox = (query: HelpInboxQuery, enabled = true) =>
  useQuery({ queryKey: helpKeys.inbox(query), queryFn: () => getHelpInbox(query), enabled,
    refetchInterval: useLiveInterval('staff', INBOX_POLL_MS), refetchIntervalInBackground: false });

export const useHelpInboxUnread = (enabled: boolean) =>
  useQuery({ queryKey: helpKeys.inboxUnread(), queryFn: getHelpInboxUnread, enabled,
    refetchInterval: useLiveInterval('staff', BADGE_POLL_MS), refetchIntervalInBackground: false });

export const useHelpInboxDetail = (id: number | null) =>
  useQuery({ queryKey: helpKeys.detail(id ?? 0), queryFn: () => getHelpInboxDetail(id ?? 0), enabled: id !== null,
    refetchInterval: useLiveInterval('staff', THREAD_POLL_MS), refetchIntervalInBackground: false });

export const useHelpCandidates = (id: number | null, enabled: boolean) =>
  useQuery({ queryKey: helpKeys.candidates(id ?? 0), queryFn: () => getHelpAssigneeCandidates(id ?? 0), enabled: enabled && id !== null });

function useInboxMutation<Input>(run: (input: Input) => Promise<HelpConversationDetail>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: run,
    onSuccess: (detail) => {
      client.setQueryData(helpKeys.detail(detail.conversation.id), detail);
      return Promise.all([
        client.invalidateQueries({ queryKey: helpKeys.inboxes() }),
        client.invalidateQueries({ queryKey: helpKeys.inboxUnread() }),
      ]);
    },
  });
}

export const useHelpReply = () =>
  useInboxMutation(({ id, message, clientMessageId }: { id: number; message: string; clientMessageId: string }) =>
    replyToHelpConversation(id, message, clientMessageId));

export const useHelpMarkRead = () => useInboxMutation(({ id, upTo }: { id: number; upTo: number }) => markHelpConversationRead(id, upTo));

export const useHelpStatus = () =>
  useInboxMutation(({ id, status }: { id: number; status: HelpConversationStatus }) => setHelpConversationStatus(id, status));

export const useHelpAssign = () => useInboxMutation(({ id, userId }: { id: number; userId: number | null }) => assignHelpConversation(id, userId));
