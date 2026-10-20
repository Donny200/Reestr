import type {
  AuthUser,
  ChangePasswordRequest,
  LoginOutcome,
  LoginRequest,
  MfaConfirmResult,
  MfaEnrollment,
  MfaStatus,
  PasswordResetOptions,
  SessionInfo,
} from '../types/api';
import { ApiError, apiRequest, refreshSession, SESSIONLESS } from './http';

/** 200 with the user, or 202 with a pending second factor (only a challenge cookie exists then). */
export const login = (payload: LoginRequest) =>
  apiRequest<AuthUser | LoginOutcome>('/api/auth/login', { method: 'POST', body: payload, ...SESSIONLESS });

export const getMfaStatus = () => apiRequest<MfaStatus>('/api/auth/mfa');

export const verifyMfa = (code: string) =>
  apiRequest<AuthUser>('/api/auth/mfa/verify', { method: 'POST', body: { code }, ...SESSIONLESS });

export const startMfaEnrollment = () => apiRequest<MfaEnrollment>('/api/auth/mfa/enroll', { method: 'POST', ...SESSIONLESS });

export const confirmMfaEnrollment = (code: string) =>
  apiRequest<MfaConfirmResult>('/api/auth/mfa/confirm', { method: 'POST', body: { code }, ...SESSIONLESS });

export const disableMfa = (password: string, code: string) =>
  apiRequest<MfaStatus>('/api/auth/mfa/disable', { method: 'POST', body: { password, code } });

export const regenerateRecoveryCodes = (password: string, code: string) =>
  apiRequest<MfaConfirmResult>('/api/auth/mfa/recovery-codes', { method: 'POST', body: { password, code } });

export const getSessions = () => apiRequest<SessionInfo[]>('/api/auth/sessions');

export const revokeSession = (sessionId: string) =>
  apiRequest<void>(`/api/auth/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' });

export const revokeOtherSessions = () => apiRequest<{ revoked: number }>('/api/auth/sessions/revoke-others', { method: 'POST' });

export async function refresh(): Promise<void> {
  if (!(await refreshSession())) throw new ApiError(401, 'Session expired');
}

export const logout = () => apiRequest<void>('/api/auth/logout', { method: 'POST', skipRefresh: true });

export const getCurrentUser = () => apiRequest<AuthUser>('/api/auth/me', { skipUnauthorizedHandler: true });

export const changePassword = (payload: ChangePasswordRequest) =>
  apiRequest<void>('/api/auth/change-password', { method: 'POST', body: payload });

export const getPasswordResetOptions = () =>
  apiRequest<PasswordResetOptions>('/api/auth/password-reset/options', SESSIONLESS);

/** Always answers the same way whether or not the address belongs to an account. */
export const requestPasswordReset = (email: string) =>
  apiRequest<void>('/api/auth/password-reset/request', { method: 'POST', body: { email }, ...SESSIONLESS });

export const completePasswordReset = (token: string, newPassword: string) =>
  apiRequest<void>('/api/auth/password-reset/complete', { method: 'POST', body: { token, newPassword }, ...SESSIONLESS });
