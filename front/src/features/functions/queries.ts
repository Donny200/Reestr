import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useAuth } from '../../contexts/auth';
import {
  assignFunction,
  backfillTranslations,
  createFunction,
  createFunctionRevision,
  getAssigneeCandidates,
  getAssignmentOverview,
  unassignFunction,
  deactivateFunction,
  discardFunctionRevision,
  getAdminFunction,
  getAdminFunctions,
  getFunctionAudit,
  getFunctionCategories,
  getTranslationCapabilities,
  requeueTranslation,
  saveFunctionTranslation,
  transitionFunction,
  translateFunction,
  updateFunction,
  verifyFunction,
  type FunctionTranslationBody,
} from '../../services/adminFunctionService';
import { getFunction, getFunctionHistory, getFunctions, updateFunctionRequirements } from '../../services/functionService';
import { getPublicOrganizations } from '../../services/organizationService';
import type {
  AdminFunction,
  CreateFunctionRequest,
  FunctionCategory,
  FunctionOrganization,
  FunctionTransition,
  UpdateFunctionRequest,
} from '../../types/adminFunctions';
import type { FunctionQuery } from '../../types/api';
import type { AssignmentState } from '../../types/analytics';
import { analyticsKeys, functionKeys } from '../queryKeys';

export const useAdminFunctions = (enabled = true) =>
  useQuery({ queryKey: functionKeys.admin(), queryFn: getAdminFunctions, enabled });

const POLL_WHILE_TRANSLATING_MS = 10_000;

export const useAdminFunction = (id: number) =>
  useQuery({
    queryKey: functionKeys.detail(id),
    queryFn: () => getAdminFunction(id),
    enabled: Number.isFinite(id),
    // While the background worker still owes this service a translation, refresh so staff see it land.
    refetchInterval: (query) => {
      const status = query.state.data?.machineTranslation?.status;
      return status === 'PENDING' || status === 'RUNNING' ? POLL_WHILE_TRANSLATING_MS : false;
    },
  });

export const useFunctionCategories = () =>
  useQuery({ queryKey: functionKeys.categories(), queryFn: getFunctionCategories, staleTime: 5 * 60_000 });

export const useFunctionAudit = (id: number) =>
  useQuery({ queryKey: functionKeys.audit(id), queryFn: () => getFunctionAudit(id) });

export const useTranslationCapabilities = () =>
  useQuery({ queryKey: functionKeys.capabilities(), queryFn: getTranslationCapabilities, staleTime: 5 * 60_000 });

export const usePublicFunctions = (query: FunctionQuery = {}) =>
  useQuery({ queryKey: functionKeys.publicList(query), queryFn: () => getFunctions(query) });

export const usePublicFunction = (id: number) =>
  useQuery({ queryKey: functionKeys.publicDetail(id), queryFn: () => getFunction(id), enabled: Number.isFinite(id) });

export const usePublicFunctionHistory = (id: number | undefined) =>
  useQuery({ queryKey: functionKeys.publicHistory(id ?? 0), queryFn: () => getFunctionHistory(id ?? 0), enabled: id !== undefined, retry: false });

export const useAssignmentOverview = (organizationId: number | undefined, state: AssignmentState | undefined, enabled = true) =>
  useQuery({ queryKey: analyticsKeys.assignments(organizationId, state), queryFn: () => getAssignmentOverview(organizationId, state), enabled });

export const useAssigneeCandidates = (id: number, enabled = true) =>
  useQuery({ queryKey: functionKeys.assigneeCandidates(id), queryFn: () => getAssigneeCandidates(id), enabled });

export interface FunctionOptions {
  organizations: FunctionOrganization[];
  categories: FunctionCategory[];
}

export function useFunctionOptions() {
  const { user, isSuperAdmin, hasPermission } = useAuth();
  const global = isSuperAdmin || hasPermission('FUNCTIONS_MANAGE_ANY_ORGANIZATION');
  const ownOrganizations = user?.organizations ?? [];
  return useQuery<FunctionOptions>({
    queryKey: functionKeys.options(global, user?.id ?? null),
    queryFn: async () => {
      const [organizations, categories] = await Promise.all([
        global ? getPublicOrganizations() : Promise.resolve<FunctionOrganization[]>(ownOrganizations),
        getFunctionCategories(),
      ]);
      return { organizations, categories };
    },
  });
}

async function storeFunction(client: QueryClient, record: AdminFunction) {
  client.setQueryData(functionKeys.detail(record.id), record);
  const related = [record.id, record.revisionOfId ?? null, record.revision?.id ?? null].filter((id): id is number => id !== null);
  await Promise.all([
    client.invalidateQueries({ queryKey: functionKeys.admin() }),
    client.invalidateQueries({ queryKey: functionKeys.public() }),
    // Workflow responses do not embed the pending-revision summary; refetch the detail and every related record.
    ...related.flatMap((id) => [
      client.invalidateQueries({ queryKey: functionKeys.detail(id) }),
      client.invalidateQueries({ queryKey: functionKeys.audit(id) }),
    ]),
  ]);
}

function useFunctionMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<AdminFunction>) {
  const client = useQueryClient();
  return useMutation({ mutationFn, onSuccess: (record) => storeFunction(client, record) });
}

export const useCreateFunction = () => useFunctionMutation((body: CreateFunctionRequest) => createFunction(body));

export const useUpdateFunction = (id: number) => useFunctionMutation((body: UpdateFunctionRequest) => updateFunction(id, body));

export const useTransitionFunction = (id: number) =>
  useFunctionMutation(({ action, reason }: { action: FunctionTransition; reason?: string }) => transitionFunction(id, action, reason));

export const useDeactivateFunction = (id: number) =>
  useFunctionMutation(async () => {
    await deactivateFunction(id);
    return getAdminFunction(id);
  });

export const useSaveFunctionTranslation = (id: number) =>
  useFunctionMutation(({ language, ...body }: FunctionTranslationBody & { language: string }) =>
    saveFunctionTranslation(id, language, body));

export const useVerifyFunction = (id: number) => useFunctionMutation(() => verifyFunction(id));

export const useCreateFunctionRevision = (id: number) => useFunctionMutation(() => createFunctionRevision(id));

/** Discards the draft revision of published service {id} and reloads the published record. */
export const useDiscardFunctionRevision = (id: number) =>
  useFunctionMutation(async () => {
    await discardFunctionRevision(id);
    return getAdminFunction(id);
  });

export const useAutoTranslateFunction = (id: number) =>
  useFunctionMutation(({ languages, overwriteMachine }: { languages: string[]; overwriteMachine: boolean }) =>
    translateFunction(id, languages, overwriteMachine));

/** Re-queues the background translation and reflects the new job state on the cached record. */
export function useRequeueTranslation(id: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => requeueTranslation(id),
    onSuccess: async (summary) => {
      client.setQueryData<AdminFunction>(functionKeys.detail(id), (current) =>
        current ? { ...current, machineTranslation: summary } : current);
      await client.invalidateQueries({ queryKey: functionKeys.admin() });
    },
  });
}

function useAssignmentMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<AdminFunction>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async (record) => {
      await storeFunction(client, record);
      await client.invalidateQueries({ queryKey: analyticsKeys.all });
    },
  });
}

export const useAssignFunction = (id: number) => useAssignmentMutation((userId: number) => assignFunction(id, userId));

export const useUnassignFunction = (id: number) => useAssignmentMutation(() => unassignFunction(id));

export const useUpdateFunctionRequirements = (id: number) =>
  useFunctionMutation(async (requirements: string) => {
    await updateFunctionRequirements(id, { requirements });
    return getAdminFunction(id);
  });

export function useBackfillTranslations() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (includePublished: boolean) => backfillTranslations(includePublished),
    onSuccess: () => client.invalidateQueries({ queryKey: functionKeys.all }),
  });
}
