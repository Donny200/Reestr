import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addLanguage,
  deleteLanguage,
  getInterfaceTranslationOverview,
  getLanguages,
  queueInterfaceMachineTranslation,
} from '../../services/referenceService';
import { languageKeys } from '../queryKeys';

export const useLanguages = () => useQuery({ queryKey: languageKeys.list(), queryFn: getLanguages });

function useLanguageMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => client.invalidateQueries({ queryKey: languageKeys.all }) });
}

export const useAddLanguage = () => useLanguageMutation((code: string) => addLanguage(code));
export const useDeleteLanguage = () => useLanguageMutation((id: number) => deleteLanguage(id));

/** Polls while a job is open so the coverage shown follows the background worker. */
export const useTranslationOverview = (enabled: boolean) =>
  useQuery({
    queryKey: languageKeys.coverage(),
    queryFn: getInterfaceTranslationOverview,
    enabled,
    refetchInterval: (query) =>
      query.state.data?.languages.some((item) => item.job?.status === 'PENDING' || item.job?.status === 'RUNNING') ? 15_000 : false,
  });

export const useQueueMachineTranslation = () => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => queueInterfaceMachineTranslation(code),
    onSuccess: () => client.invalidateQueries({ queryKey: languageKeys.coverage() }),
  });
};
