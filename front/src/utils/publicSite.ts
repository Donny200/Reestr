/**
 * Addresses that may appear on printed pages. The page's own origin is never used (it can be a development server
 * such as http://localhost:5173); only the canonical public address configured on the server is printed, and even
 * that is dropped when it points to a development or private host.
 */
const PRIVATE_IPV4 = /^(127\.|10\.|0\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)/;

export function isDevelopmentHost(hostname: string): boolean {
  const host = hostname.toLowerCase().replace(/^\[|\]$/g, '');
  return host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') || host.endsWith('.internal')
    || host === '::1' || host === '0.0.0.0' || PRIVATE_IPV4.test(host) || /^f[cd]/.test(host) || host.startsWith('fe80')
    || (!host.includes('.') && !host.includes(':'));
}

export function isPublicWebLink(value: string): boolean {
  try {
    const url = new URL(value);
    return (url.protocol === 'https:' || url.protocol === 'http:') && !isDevelopmentHost(url.hostname);
  } catch {
    return false;
  }
}

/** Canonical origin without trailing slash, or null when not configured or not public. */
export function canonicalSiteUrl(configured: string | null | undefined): string | null {
  if (!configured?.trim()) return null;
  const value = configured.trim().replace(/\/+$/, '');
  return isPublicWebLink(value) ? value : null;
}

/** Printed address of a page such as /functions/3, or null when no public address is configured. */
export function printedPageUrl(configured: string | null | undefined, path: string): string | null {
  const base = canonicalSiteUrl(configured);
  return base ? `${base}${path.startsWith('/') ? path : `/${path}`}` : null;
}
