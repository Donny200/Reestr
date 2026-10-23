import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addLanguage,
  deleteLanguage,
  getInterfaceTranslationOverview,
  getLanguages,
  importInterfaceTranslations,
  queueInterfaceMachineTranslation,
} from '../../services/referenceService';
import { translationOverviewRefetchInterval } from '../../utils/translationOverview';
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
    refetchInterval: (query) => translationOverviewRefetchInterval(query.state.data),
  });

export const useQueueMachineTranslation = () => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => queueInterfaceMachineTranslation(code),
    onSuccess: () => client.invalidateQueries({ queryKey: languageKeys.coverage() }),
  });
};

export const useImportTranslations = () => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ code, backup }: { code: string; backup: unknown }) => importInterfaceTranslations(code, backup),
    onSuccess: () => client.invalidateQueries({ queryKey: languageKeys.coverage() }),
  });
};
