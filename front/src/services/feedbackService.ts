import { apiRequest, SESSIONLESS } from './http';

export interface FeedbackVote {
  serviceId: number;
  helpful: boolean;
}

/** Anonymous, aggregate-only vote: no identifiers travel with it and the request never triggers a session refresh. */
export const submitFeedback = (vote: FeedbackVote) =>
  apiRequest<void>('/api/feedback', { method: 'POST', body: vote, ...SESSIONLESS });
