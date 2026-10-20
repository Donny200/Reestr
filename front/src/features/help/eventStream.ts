import { API_BASE_URL } from '../../config/env';
import { refreshSession } from '../../services/http';

/**
 * Minimal server-sent events client over fetch (EventSource cannot report status codes such as 204 or 401).
 *
 * The server only says that something changed; callers reload through the normal REST endpoints, which remain the
 * source of truth. Every (re)connection starts with a "ready" event, after which the caller reloads everything, so
 * events missed while disconnected are repaired. Event ids are remembered to drop duplicates.
 */
export type StreamState = 'connecting' | 'live' | 'retrying' | 'unavailable' | 'paused';

export interface StreamEvent {
  id: string | null;
  event: string;
  data: string;
}

export interface EventStreamOptions {
  path: string;
  onEvent: (event: StreamEvent) => void;
  /** Called on every successful (re)connection, before any other event. */
  onReady: () => void;
  onState: (state: StreamState) => void;
  /** Staff streams: try to refresh the session once on 401. */
  refreshOnUnauthorized?: boolean;
}

const BACKOFF_MS = [1_000, 2_000, 5_000, 10_000, 30_000];
const SEEN_LIMIT = 200;

/** Parses complete SSE frames from a text buffer; returns the frames and the unparsed remainder. */
export function parseEventFrames(buffer: string): { events: StreamEvent[]; rest: string; retry?: number } {
  const normalized = buffer.replace(/\r\n?/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const events: StreamEvent[] = [];
  let retry: number | undefined;
  for (const block of blocks) {
    let id: string | null = null;
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue;
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'id') id = value;
      else if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
      else if (field === 'retry' && /^\d+$/.test(value)) retry = Number(value);
    }
    if (data.length > 0 || id !== null) events.push({ id, event, data: data.join('\n') });
  }
  return { events, rest, retry };
}

/** Opens a stream that reconnects with back-off; returns a function that closes it for good. */
export function openEventStream(options: EventStreamOptions): () => void {
  let closed = false;
  let attempt = 0;
  let timer: number | undefined;
  let controller: AbortController | null = null;
  let retryOverride: number | undefined;
  const seen: string[] = [];
  const seenSet = new Set<string>();

  const remember = (id: string) => {
    seen.push(id);
    seenSet.add(id);
    if (seen.length > SEEN_LIMIT) seenSet.delete(seen.shift() as string);
  };

  const schedule = (delay?: number) => {
    if (closed) return;
    options.onState('retrying');
    const base = delay ?? Math.max(retryOverride ?? 0, BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)]);
    attempt += 1;
    timer = window.setTimeout(() => void connect(), base + Math.floor(Math.random() * 500));
  };

  const connect = async (afterRefresh = false): Promise<void> => {
    if (closed) return;
    options.onState('connecting');
    controller = new AbortController();
    let response: Response;
    try {
      response = await fetch(`${API_BASE_URL.replace(/\/$/, '')}${options.path}`, {
        credentials: 'include',
        headers: { Accept: 'text/event-stream' },
        cache: 'no-store',
        signal: controller.signal,
      });
    } catch {
      if (!closed) schedule();
      return;
    }
    if (response.status === 204) {
      options.onState('unavailable');
      return;
    }
    if (response.status === 401 && options.refreshOnUnauthorized && !afterRefresh) {
      if (await refreshSession()) return connect(true);
      options.onState('unavailable');
      return;
    }
    if (response.status === 401 || response.status === 403) {
      options.onState('unavailable');
      return;
    }
    if (response.status === 429) {
      const seconds = Number(response.headers.get('Retry-After') ?? '30');
      schedule((Number.isFinite(seconds) ? seconds : 30) * 1000);
      return;
    }
    if (!response.ok || !response.body) {
      schedule();
      return;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parsed = parseEventFrames(buffer);
        buffer = parsed.rest;
        if (parsed.retry !== undefined) retryOverride = parsed.retry;
        for (const event of parsed.events) {
          if (event.event === 'ready') {
            attempt = 0;
            options.onState('live');
            options.onReady();
            continue;
          }
          if (event.id) {
            if (seenSet.has(event.id)) continue;
            remember(event.id);
          }
          options.onEvent(event);
        }
      }
    } catch {
      // Aborted or the connection broke; reconnect below unless closed.
    }
    if (!closed) schedule(attempt === 0 ? 1_000 : undefined);
  };

  void connect();

  return () => {
    closed = true;
    window.clearTimeout(timer);
    controller?.abort();
  };
}
