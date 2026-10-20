import type { AssignRoleRequest, CreateRoleRequest, Permission, RoleEntity, UpdateRolePermissionsRequest } from '../types/api';
import { apiRequest } from './http';

export const getPermissions = () => apiRequest<Permission[]>('/api/permissions');

export const getRoles = () => apiRequest<RoleEntity[]>('/api/roles');

export const createRole = (payload: CreateRoleRequest) => apiRequest<RoleEntity>('/api/roles', { method: 'POST', body: payload });

export const deleteRole = (id: number) => apiRequest<void>(`/api/roles/${id}`, { method: 'DELETE' });

export const assignRole = (userId: number, roleId: number) => {
  const body: AssignRoleRequest = { userId, roleId };
  return apiRequest<string>('/api/roles/assign', { method: 'POST', body });
};

export const updateRolePermissions = (id: number, payload: UpdateRolePermissionsRequest) =>
  apiRequest<RoleEntity>(`/api/roles/${id}/permissions`, { method: 'PUT', body: payload });
