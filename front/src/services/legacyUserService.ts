import type {
  CreateLegacyUserRequest,
  LegacyUser,
  LegacyUserApi,
  PasswordResetLink,
  SessionInfo,
  UpdateLegacyUserRequest,
} from '../types/api';
import { apiRequest } from './http';
import { toStaffUser } from './staffService';

function normalize(user: LegacyUserApi): LegacyUser {
  const { organizationIds, ...rest } = toStaffUser(user);
  return { ...rest, organizations: organizationIds, mfa: user.mfa ?? null };
}

export const getLegacyUsers = async (): Promise<LegacyUser[]> => {
  const users = await apiRequest<LegacyUserApi[]>('/api/user');
  return users.map(normalize);
};

export const createLegacyUser = async (payload: CreateLegacyUserRequest): Promise<LegacyUser> => {
  const created = await apiRequest<LegacyUserApi>('/api/user', {
    method: 'POST',
    body: {
      firstName: payload.firstName,
      lastName: payload.lastName,
      email: payload.email,
      password: payload.password,
      phone: payload.phone || null,
      roleId: payload.roleId,
      organizationIds: payload.organizationIds,
    },
  });
  return normalize(created);
};

export const updateLegacyUser = async (id: number, payload: UpdateLegacyUserRequest): Promise<LegacyUser> =>
  normalize(await apiRequest<LegacyUserApi>(`/api/user/${id}`, { method: 'PUT', body: payload }));

export const deactivateLegacyUser = (id: number) => apiRequest<void>(`/api/user/${id}`, { method: 'DELETE' });

/** SUPER_ADMIN only: issues a single-use reset link. Passwords are never returned or displayed. */
export const initiatePasswordReset = (id: number) =>
  apiRequest<PasswordResetLink>(`/api/user/${id}/password-reset`, { method: 'POST' });

/** SUPER_ADMIN only: removes the second factor and ends every session; the user re-enrolls at the next sign-in. */
export const resetUserMfa = (id: number) => apiRequest<void>(`/api/user/${id}/mfa/reset`, { method: 'POST' });

export const getUserSessions = (id: number) => apiRequest<SessionInfo[]>(`/api/user/${id}/sessions`);

export const revokeUserSession = (id: number, sessionId: string) =>
  apiRequest<void>(`/api/user/${id}/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' });

export const revokeUserSessions = (id: number) => apiRequest<void>(`/api/user/${id}/sessions`, { method: 'DELETE' });
