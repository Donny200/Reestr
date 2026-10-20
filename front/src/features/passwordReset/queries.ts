import { useQuery, type UseQueryOptions } from '@tanstack/react-query';
import { getPasswordResetOptions } from '../../services/authService';
import type { PasswordResetOptions } from '../../types/api';
import { authKeys } from '../queryKeys';

export const usePasswordResetOptions = (options: Pick<UseQueryOptions<PasswordResetOptions>, 'retry'> = {}) =>
  useQuery({ queryKey: authKeys.passwordResetOptions(), queryFn: getPasswordResetOptions, staleTime: 60_000, ...options });
