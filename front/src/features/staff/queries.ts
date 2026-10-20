import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getModeratorCandidates, getModerators, getOrgAdminCandidates, getOrgAdmins } from '../../services/staffService';
import { legacyUserKeys, roleKeys, staffKeys } from '../queryKeys';

export const useOrgAdmins = (enabled = true) => useQuery({ queryKey: staffKeys.orgAdmins(), queryFn: getOrgAdmins, enabled });
export const useOrgAdminCandidates = () => useQuery({ queryKey: staffKeys.orgAdminCandidates(), queryFn: getOrgAdminCandidates });
export const useModerators = (enabled = true) => useQuery({ queryKey: staffKeys.moderators(), queryFn: getModerators, enabled });
export const useModeratorCandidates = () => useQuery({ queryKey: staffKeys.moderatorCandidates(), queryFn: getModeratorCandidates });

export function useStaffMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: staffKeys.all }),
        client.invalidateQueries({ queryKey: legacyUserKeys.all }),
        client.invalidateQueries({ queryKey: roleKeys.assignmentCandidates() }),
      ]),
  });
}

