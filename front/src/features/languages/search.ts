import { useQuery } from '@tanstack/react-query';
import { searchLanguages } from '../../services/referenceService';
import { languageKeys } from '../queryKeys';

export const useLanguageSearch = (query: string) =>
  useQuery({
    queryKey: languageKeys.search(query),
    queryFn: () => searchLanguages(query),
    enabled: query.length > 0,
    staleTime: 60_000,
  });
