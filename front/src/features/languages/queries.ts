import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addLanguage, deleteLanguage, getLanguages } from '../../services/referenceService';
import { languageKeys } from '../queryKeys';

export const useLanguages = () => useQuery({ queryKey: languageKeys.list(), queryFn: getLanguages });

function useLanguageMutation<TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => client.invalidateQueries({ queryKey: languageKeys.all }) });
}

export const useAddLanguage = () => useLanguageMutation((code: string) => addLanguage(code));
export const useDeleteLanguage = () => useLanguageMutation((id: number) => deleteLanguage(id));
