import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createOrganization,
  deactivateOrganization,
  getOrganizations,
  getPublicOrganization,
  getPublicOrganizations,
  reactivateOrganization,
  updateOrganization,
  verifyOrganization,
} from '../../services/organizationService';
import type { CreateOrganizationRequest } from '../../types/api';
import { organizationKeys } from '../queryKeys';

export const useOrganizations = () => useQuery({ queryKey: organizationKeys.list(), queryFn: getOrganizations });

export const usePublicOrganizations = () => useQuery({ queryKey: organizationKeys.public(), queryFn: getPublicOrganizations });

export const usePublicOrganization = (id: number) =>
  useQuery({ queryKey: organizationKeys.publicDetail(id), queryFn: () => getPublicOrganization(id), enabled: Number.isFinite(id) });

export interface SaveOrganizationInput {
  id?: number;
  payload: CreateOrganizationRequest;
}

function useOrganizationMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => client.invalidateQueries({ queryKey: organizationKeys.all }) });
}

export const useSaveOrganization = () =>
  useOrganizationMutation(({ id, payload }: SaveOrganizationInput) => (id ? updateOrganization(id, payload) : createOrganization(payload)));

export const useDeactivateOrganization = () => useOrganizationMutation((id: number) => deactivateOrganization(id));

export const useReactivateOrganization = () => useOrganizationMutation((id: number) => reactivateOrganization(id));

export const useVerifyOrganization = () => useOrganizationMutation((id: number) => verifyOrganization(id));
