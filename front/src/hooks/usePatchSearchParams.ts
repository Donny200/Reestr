import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/** Merges changes into the current query string in place; an empty value removes the parameter. */
export function usePatchSearchParams() {
  const [params, setParams] = useSearchParams();
  const patch = useCallback(
    (changes: Record<string, string>) =>
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          Object.entries(changes).forEach(([name, value]) => (value ? next.set(name, value) : next.delete(name)));
          return next;
        },
        { replace: true },
      ),
    [setParams],
  );
  return [params, patch, setParams] as const;
}
