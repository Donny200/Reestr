import { useEffect, useState } from 'react';
import { ThumbsDownIcon, ThumbsUpIcon } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { useAuth } from '../../contexts/auth';
import { useI18n } from '../../contexts/i18n';
import { submitFeedback } from '../../services/feedbackService';
import { recordedVote, rememberVote } from './feedbackStore';

/**
 * "Was this information helpful?" for public visitors. Sends only the service id and a yes/no; the browser remembers
 * the answer so the question is not repeated. Staff are not asked, mirroring engagement tracking.
 */
export function FeedbackPrompt({ serviceId, onReport }: { serviceId: number; onReport?: () => void }) {
  const { t } = useI18n();
  const { user, initializing } = useAuth();
  const [state, setState] = useState<'idle' | 'sending' | 'thanks' | 'error'>('idle');
  const [answered, setAnswered] = useState<boolean | null>(null);

  useEffect(() => {
    const vote = recordedVote(serviceId);
    setAnswered(vote ? vote.helpful : null);
    setState('idle');
  }, [serviceId]);

  if (initializing || user) return null;

  const vote = async (helpful: boolean) => {
    setState('sending');
    try {
      await submitFeedback({ serviceId, helpful });
      rememberVote(serviceId, helpful);
      setAnswered(helpful);
      setState('thanks');
    } catch {
      setState('error');
    }
  };

  const done = answered !== null;
  return (
    <section className="mt-10 rounded-card-sm border border-line bg-surface p-4 print:hidden sm:p-5" aria-labelledby="feedback-title">
      <h2 id="feedback-title" className="text-base font-semibold text-foreground">{t('feedback.question', 'Was this information helpful?')}</h2>
      <p className="mt-1 text-sm leading-6 text-secondary">
        {t('feedback.scope', 'Your answer is anonymous and only counts whether this page was useful. It is not a rating of the organization, the service or its staff.')}
      </p>
      {done ? (
        <div className="mt-4 space-y-2" role="status" aria-live="polite">
          <p className="text-sm font-medium text-foreground">{t('feedback.thanks', 'Thank you, your answer was counted.')}</p>
          {onReport && (
            <p className="text-sm leading-6 text-secondary">
              {t('feedback.reportHint', 'Found a concrete mistake? Use “Report a problem” so the organization can fix it.')}{' '}
              <button type="button" onClick={onReport} className="rounded-sm font-medium text-accent-text underline-offset-4 fine:hover:underline">
                {t('report.open', 'Report a problem')}
              </button>
            </p>
          )}
        </div>
      ) : (
        <div className="mt-4 flex flex-wrap gap-2" role="group" aria-labelledby="feedback-title">
          <Button variant="outline" size="sm" icon={<ThumbsUpIcon />} loading={state === 'sending'} disabled={state === 'sending'} onClick={() => void vote(true)}>
            {t('feedback.yes', 'Yes')}
          </Button>
          <Button variant="outline" size="sm" icon={<ThumbsDownIcon />} loading={state === 'sending'} disabled={state === 'sending'} onClick={() => void vote(false)}>
            {t('feedback.no', 'No')}
          </Button>
        </div>
      )}
      {state === 'error' && <p className="mt-3 text-sm text-status-danger" role="alert">{t('feedback.failed', 'Your answer could not be sent right now. Please try again later.')}</p>}
    </section>
  );
}
