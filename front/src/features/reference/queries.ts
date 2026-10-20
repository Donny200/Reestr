import { useQuery } from '@tanstack/react-query';
import { getRegions } from '../../services/referenceService';
import { referenceKeys } from '../queryKeys';

export const useRegions = () => useQuery({ queryKey: referenceKeys.regions, queryFn: getRegions, staleTime: 10 * 60_000 });
