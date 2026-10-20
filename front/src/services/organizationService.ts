import type { CreateOrganizationRequest, Organization, PublicOrganization, UpdateOrganizationRequest } from '../types/api';
import { apiRequest } from './http';

export const getPublicOrganizations = () => apiRequest<PublicOrganization[]>('/api/public/organizations');

export const getPublicOrganization = (id: number) => apiRequest<PublicOrganization>(`/api/public/organizations/${id}`);

export const getOrganizations = () => apiRequest<Organization[]>('/api/organizations');

export const createOrganization = (payload: CreateOrganizationRequest) =>
  apiRequest<Organization>('/api/organizations', { method: 'POST', body: payload });

export const updateOrganization = (id: number, payload: UpdateOrganizationRequest) =>
  apiRequest<Organization>(`/api/organizations/${id}`, { method: 'PUT', body: payload });

export const deactivateOrganization = (id: number) => apiRequest<void>(`/api/organizations/${id}`, { method: 'DELETE' });

export const reactivateOrganization = (id: number) =>
  apiRequest<Organization>(`/api/organizations/${id}/reactivate`, { method: 'POST' });

export const verifyOrganization = (id: number) =>
  apiRequest<Organization>(`/api/organizations/${id}/verify`, { method: 'POST' });
