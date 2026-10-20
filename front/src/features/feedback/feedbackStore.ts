/**
 * Remembers in this browser which services the visitor already answered "Was this information helpful?" for, so the
 * control is not shown again for a while. It is best-effort anti-duplication only and never leaves the browser.
 */
export const FEEDBACK_KEY = 'reestr-feedback-votes';
const REMEMBER_MS = 180 * 24 * 60 * 60_000;

interface StoredVote {
  helpful: boolean;
  at: number;
}

function read(): Record<string, StoredVote> {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(FEEDBACK_KEY) ?? '{}') as Record<string, StoredVote>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

export function recordedVote(serviceId: number): StoredVote | null {
  const vote = read()[String(serviceId)];
  if (!vote || typeof vote.at !== 'number' || Date.now() - vote.at > REMEMBER_MS) return null;
  return vote;
}

export function rememberVote(serviceId: number, helpful: boolean): boolean {
  try {
    const votes = read();
    const now = Date.now();
    const kept = Object.fromEntries(Object.entries(votes).filter(([, vote]) => typeof vote?.at === 'number' && now - vote.at <= REMEMBER_MS));
    kept[String(serviceId)] = { helpful, at: now };
    window.localStorage.setItem(FEEDBACK_KEY, JSON.stringify(kept));
    return true;
  } catch {
    return false;
  }
}
