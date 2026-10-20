import { useMutation, useQuery } from '@tanstack/react-query';
import { createLegacyUser, deactivateLegacyUser, getLegacyUsers, initiatePasswordReset, resetUserMfa, updateLegacyUser } from '../../services/legacyUserService';
import type { UpdateLegacyUserRequest } from '../../types/api';
import { legacyUserKeys } from '../queryKeys';
import { useStaffMutation } from '../staff/queries';

export const useLegacyUsers = () => useQuery({ queryKey: legacyUserKeys.list(), queryFn: getLegacyUsers });

export const useCreateLegacyUser = () => useStaffMutation(createLegacyUser);
export const useUpdateLegacyUser = () =>
  useStaffMutation(({ id, payload }: { id: number; payload: UpdateLegacyUserRequest }) => updateLegacyUser(id, payload));
export const useDeactivateLegacyUser = () => useStaffMutation((id: number) => deactivateLegacyUser(id));
export const useInitiatePasswordReset = () => useMutation({ mutationFn: (id: number) => initiatePasswordReset(id) });
export const useResetUserMfa = () => useStaffMutation((id: number) => resetUserMfa(id));
