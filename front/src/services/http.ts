import { API_BASE_URL } from '../config/env';

export interface FieldIssue {
  field: string;
  message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldIssue[];
  readonly payload: unknown;

  constructor(status: number, message: string, fieldErrors: FieldIssue[] = [], payload?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.payload = payload;
  }
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

export function onUnauthorized(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Extra request headers, e.g. a visitor's private conversation token (kept out of URLs and server logs). */
  headers?: Record<string, string>;
  skipRefresh?: boolean;
  skipUnauthorizedHandler?: boolean;
  signal?: AbortSignal;
}

/** For calls made outside a staff session (sign-in, visitors, public pages): a 401 neither refreshes nor signs anyone out. */
export const SESSIONLESS = { skipRefresh: true, skipUnauthorizedHandler: true } as const;

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  const base = API_BASE_URL.replace(/\/$/, '');
  const target = path.startsWith('http') ? path : `${base}${path}`;
  const url = new URL(target, origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }
  return url.toString();
}

function extractFieldErrors(payload: unknown): FieldIssue[] {
  if (!payload || typeof payload !== 'object') return [];
  const record = payload as Record<string, unknown>;

  if (Array.isArray(record.errors)) {
    return record.errors.
    map((item) => {
      const entry = item as Record<string, unknown>;
      const field = String(entry.field ?? entry.property ?? '');
      const message = String(entry.message ?? entry.defaultMessage ?? '');
      return { field, message };
    }).
    filter((issue) => issue.field && issue.message);
  }

  if (record.fieldErrors && typeof record.fieldErrors === 'object') {
    return Object.entries(record.fieldErrors as Record<string, string>).map(([field, message]) => ({
      field,
      message: String(message)
    }));
  }

  return [];
}

let refreshInFlight: Promise<boolean> | null = null;

export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch(buildUrl('/api/auth/refresh'), {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' }
    }).
    then((response) => response.ok).
    catch(() => false).
    finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function messageFor(status: number, payload: unknown): string {
  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>;
    const raw = record.message ?? record.error ?? record.detail;
    if (typeof raw === 'string' && raw.trim()) return raw;
  }
  if (typeof payload === 'string' && payload.trim()) return payload;
  return `HTTP ${status}`;
}

export interface DownloadResult {
  filename: string;
  truncated: boolean;
}

function filenameOf(header: string | null): string | null {
  if (!header) return null;
  const encoded = header.match(/filename\*=UTF-8''([^;]+)/i);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = header.match(/filename="?([^";]+)"?/i);
  return plain ? plain[1] : null;
}

async function send(url: string, init: RequestInit, { skipRefresh, skipUnauthorizedHandler }: RequestOptions = {}): Promise<Response> {
  const execute = () => fetch(url, { ...init, credentials: 'include' });

  let response: Response;
  try {
    response = await execute();
  } catch (error) {
    if ((error as Error).name === 'AbortError') throw error;
    throw new ApiError(0, 'Network error');
  }

  if (response.status === 401 && !skipRefresh) {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await execute();
    } else {
      if (!skipUnauthorizedHandler) unauthorizedHandler?.();
      throw new ApiError(401, 'Session expired');
    }
  }

  if (!response.ok) {
    const payload = await parseBody(response);
    if (response.status === 401 && !skipUnauthorizedHandler) unauthorizedHandler?.();
    throw new ApiError(response.status, messageFor(response.status, payload), extractFieldErrors(payload), payload);
  }

  return response;
}

export async function apiDownload(path: string, query?: RequestOptions['query']): Promise<DownloadResult> {
  const response = await send(buildUrl(path, query), {});
  const blob = await response.blob();
  const filename = filenameOf(response.headers.get('Content-Disposition')) ?? 'export.csv';
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  return { filename, truncated: response.headers.get('X-Export-Truncated') === 'true' };
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, headers, signal } = options;
  const response = await send(
    buildUrl(path, query),
    {
      method,
      headers: { 'Content-Type': 'application/json', ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    },
    options,
  );
  return (await parseBody(response)) as T;
}
