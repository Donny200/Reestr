import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { assignRole, createRole, deleteRole, getPermissions, getRoles, updateRolePermissions } from '../../services/roleService';
import { getModerators, getOrgAdmins, getRoleAssignmentCandidates } from '../../services/staffService';
import type { StaffUser } from '../../types/api';
import { legacyUserKeys, roleKeys, staffKeys } from '../queryKeys';

export const useRoles = () => useQuery({ queryKey: roleKeys.list(), queryFn: getRoles });
export const usePermissions = () => useQuery({ queryKey: roleKeys.permissions(), queryFn: getPermissions, staleTime: 5 * 60_000 });

export const useRoleAssignmentCandidates = () =>
  useQuery({
    queryKey: roleKeys.assignmentCandidates(),
    queryFn: async (): Promise<StaffUser[]> => {
      const [admins, moderators, plain] = await Promise.all([getOrgAdmins(), getModerators(), getRoleAssignmentCandidates()]);
      return Array.from(new Map([...admins, ...moderators, ...plain].map((user) => [user.id, user])).values());
    },
  });

function useRoleMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>, touchesUsers = false) {
  const client = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: roleKeys.all }),
        ...(touchesUsers
          ? [client.invalidateQueries({ queryKey: staffKeys.all }), client.invalidateQueries({ queryKey: legacyUserKeys.all })]
          : []),
      ]),
  });
}

export const useCreateRole = () => useRoleMutation((name: string) => createRole({ name }));
export const useDeleteRole = () => useRoleMutation((id: number) => deleteRole(id));
export const useAssignRole = () => useRoleMutation(({ userId, roleId }: { userId: number; roleId: number }) => assignRole(userId, roleId), true);
export const useUpdateRolePermissions = () =>
  useRoleMutation(({ id, permissionIds }: { id: number; permissionIds: number[] }) => updateRolePermissions(id, { permissionIds }));
