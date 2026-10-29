import { createContext, useContext } from 'react';
import type { ReviewComment } from '../../types/adminFunctions';

/**
 * What the editor shows about the review of the card: open notes above their fields for the author, and for a reviewer
 * the controls to flag a field or resolve a note. Absent (null) when the card has no review state to show.
 */
export interface ReviewFeedbackValue {
  mode: 'author' | 'reviewer';
  open: Map<string, ReviewComment>;
  busy: boolean;
  onFlag?: (path: string, label: string) => void;
  onResolve?: (comment: ReviewComment, label: string) => void;
}

export const ReviewFeedbackContext = createContext<ReviewFeedbackValue | null>(null);

export const useReviewFeedback = () => useContext(ReviewFeedbackContext);
