import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../services/http';

const NON_RETRYABLE = new Set([400, 401, 403, 404, 409]);

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => !(error instanceof ApiError && NON_RETRYABLE.has(error.status)) && failureCount < 2,
    },
  },
});
