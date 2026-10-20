/**
 * Guest access to a booking: a 256-bit secret chosen in the browser (only its hash is stored by the server). It
 * travels in the manage link's fragment (#t=…), which browsers never send to servers or put in Referer headers,
 * and in an X-Appointment-Token header for API calls. It is never stored by this site in the browser.
 */
export function newAccessToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function newRequestId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  return newAccessToken().slice(0, 32);
}

export function tokenFromHash(hash: string): string | null {
  const value = new URLSearchParams(hash.replace(/^#/, '')).get('t');
  return value && /^[A-Za-z0-9_-]{43}$/.test(value) ? value : null;
}

export function managePath(reference: string, token: string): string {
  return `/appointments/${reference}#t=${token}`;
}

export const APPOINTMENT_MANAGE_PERMISSION = 'APPOINTMENTS_MANAGE';
export const APPOINTMENT_VIEW_PERMISSIONS = ['APPOINTMENTS_VIEW', 'APPOINTMENTS_MANAGE'] as const;
