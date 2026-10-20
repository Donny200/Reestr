import type {
  CreateModeratorRequest,
  CreateOrgAdminRequest,
  LegacyUserApi,
  PromoteModeratorRequest,
  PromoteOrgAdminRequest,
  StaffUser,
  UpdateModeratorRequest,
  UpdateOrgAdminRequest,
} from '../types/api';
import { apiRequest } from './http';

export function toStaffUser(user: LegacyUserApi): StaffUser {
  return {
    id: user.id,
    firstName: user.firstName,
    lastName: user.lastName,
    email: user.email,
    phone: user.phone ?? null,
    role: user.role,
    enabled: user.enabled,
    organizationIds: user.organizationIds ?? [],
  };
}

export const getOrgAdmins = () => apiRequest<StaffUser[]>('/api/admin/org-admins');

export const createOrgAdmin = (payload: CreateOrgAdminRequest) =>
  apiRequest<StaffUser>('/api/admin/org-admins', { method: 'POST', body: payload });

export const promoteOrgAdmin = (payload: PromoteOrgAdminRequest) =>
  apiRequest<StaffUser>('/api/admin/org-admins/promote', { method: 'POST', body: payload });

export const updateOrgAdmin = (id: number, payload: UpdateOrgAdminRequest) =>
  apiRequest<StaffUser>(`/api/admin/org-admins/${id}`, { method: 'PUT', body: payload });

export const deactivateOrgAdmin = (id: number) => apiRequest<void>(`/api/admin/org-admins/${id}`, { method: 'DELETE' });

export const getModerators = () => apiRequest<StaffUser[]>('/api/admin/moderators');

export const createModerator = (payload: CreateModeratorRequest) =>
  apiRequest<StaffUser>('/api/admin/moderators', { method: 'POST', body: payload });

export const promoteModerator = (payload: PromoteModeratorRequest) =>
  apiRequest<StaffUser>('/api/admin/moderators/promote', { method: 'POST', body: payload });

export const updateModerator = (id: number, payload: UpdateModeratorRequest) =>
  apiRequest<StaffUser>(`/api/admin/moderators/${id}`, { method: 'PUT', body: payload });

export const deactivateModerator = (id: number) => apiRequest<void>(`/api/admin/moderators/${id}`, { method: 'DELETE' });

export const getRoleAssignmentCandidates = async (): Promise<StaffUser[]> => {
  const users = await apiRequest<LegacyUserApi[]>('/api/user');
  return users.map(toStaffUser);
};

export const getModeratorCandidates = () => apiRequest<StaffUser[]>('/api/admin/moderators/candidates');

export const getOrgAdminCandidates = () => apiRequest<StaffUser[]>('/api/admin/org-admins/candidates');
